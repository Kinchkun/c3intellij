package org.c3lang.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.c3lang.intellij.psi.C3Arg;
import org.c3lang.intellij.psi.C3CallInvocation;
import org.c3lang.intellij.psi.C3CallablePsiElement;
import org.c3lang.intellij.psi.C3Calls;
import org.c3lang.intellij.psi.C3PathIdentExpr;
import org.c3lang.intellij.psi.ParamType;
import org.c3lang.intellij.psi.ShortType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Completes the parameter names of the function/method being called as named arguments, e.g. typing
 * {@code par} inside {@code my_function(...)} offers {@code param} and inserts {@code param: }.
 */
public final class NamedArgumentCompletionContributor extends CompletionProvider<CompletionParameters>
{
	public static final NamedArgumentCompletionContributor INSTANCE = new NamedArgumentCompletionContributor();

	private static final double NAMED_ARGUMENT_PRIORITY = 1_000_000.0;

	/** Appends {@code ": "} after the inserted name and places the caret ready for the value. */
	private static final InsertHandler<LookupElement> INSERT_COLON = (context, item) -> {
		int tail = context.getTailOffset();
		CharSequence text = context.getDocument().getCharsSequence();
		int after = tail;
		while (after < text.length() && text.charAt(after) == ' ') after++;
		if (after < text.length() && text.charAt(after) == ':')
		{
			context.getEditor().getCaretModel().moveToOffset(after + 1);
			return;
		}
		context.getDocument().insertString(tail, ": ");
		context.getEditor().getCaretModel().moveToOffset(tail + 2);
	};

	@Override
	protected void addCompletions(
		@NotNull CompletionParameters parameters,
		@NotNull ProcessingContext context,
		@NotNull CompletionResultSet result)
	{
		PsiElement position = parameters.getPosition();

		C3Arg arg = PsiTreeUtil.getParentOfType(position, C3Arg.class);
		if (arg == null || arg.getNamedIdent() != null) return;

		// Only at the head of an argument: the whole argument value is this bare identifier, not a
		// sub-expression like `foo + par`.
		C3PathIdentExpr pathExpr = PsiTreeUtil.getParentOfType(position, C3PathIdentExpr.class);
		if (pathExpr == null || pathExpr.getParent() != arg) return;

		C3CallInvocation invocation = PsiTreeUtil.getParentOfType(arg, C3CallInvocation.class);
		if (invocation == null) return;

		List<C3CallablePsiElement> callables = C3Calls.resolveCallables(invocation);
		if (callables.isEmpty()) return;

		Set<String> alreadyNamed = namedArgumentsAlreadyPresent(invocation);
		Set<String> offered = new HashSet<>();
		for (C3CallablePsiElement callable : callables)
		{
			for (ParamType parameter : callable.getParameterTypes())
			{
				String name = parameter.getName();
				if (name == null || name.isEmpty() || alreadyNamed.contains(name) || !offered.add(name)) continue;

				ShortType type = parameter.getType();
				result.addElement(PrioritizedLookupElement.withPriority(
					LookupElementBuilder.create(name)
						.withIcon(AllIcons.Nodes.Parameter)
						.withTypeText(type != null ? type.getFullName() : "")
						.withInsertHandler(INSERT_COLON),
					// Rank named arguments above function/variable suggestions, whose priority is
					// `matchingDegree * 10 + closeness` — inside a call the parameter is what you want.
					NAMED_ARGUMENT_PRIORITY));
			}
		}
	}

	private static @NotNull Set<String> namedArgumentsAlreadyPresent(@NotNull C3CallInvocation invocation)
	{
		Set<String> names = new HashSet<>();
		for (C3Arg arg : PsiTreeUtil.findChildrenOfType(invocation, C3Arg.class))
		{
			if (arg.getNamedIdent() != null) names.add(arg.getNamedIdent().getText());
		}
		return names;
	}
}
