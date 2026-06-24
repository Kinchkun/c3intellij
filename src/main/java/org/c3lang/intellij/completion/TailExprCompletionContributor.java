package org.c3lang.intellij.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.icons.AllIcons;
import com.intellij.util.ProcessingContext;
import kotlin.Pair;
import org.c3lang.intellij.C3Icons;
import org.c3lang.intellij.index.NameIndexService;
import org.c3lang.intellij.index.StructService;
import org.c3lang.intellij.intention.C3Interfaces;
import org.c3lang.intellij.psi.AccessPath;
import org.c3lang.intellij.psi.C3AccessIdent;
import org.c3lang.intellij.psi.C3CallExprTail;
import org.c3lang.intellij.psi.C3CallablePsiElement;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3ExprStmt;
import org.c3lang.intellij.psi.C3FullyQualifiedNamePsiElement;
import org.c3lang.intellij.psi.C3ModuleDefinition;
import org.c3lang.intellij.psi.C3Types;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

public final class TailExprCompletionContributor extends CompletionProvider<CompletionParameters>
{
	public static final TailExprCompletionContributor INSTANCE = new TailExprCompletionContributor();

	private static final ElementPattern<PsiElement> PATTERN = or(
		psiElement(C3Types.IDENT).inside(C3AccessIdent.class),
		psiElement(PsiWhiteSpace.class).inside(C3CallExprTail.class)
	);

	private TailExprCompletionContributor() {}

	@Override
	protected void addCompletions(
		@NotNull CompletionParameters parameters,
		@NotNull ProcessingContext context,
		@NotNull CompletionResultSet result)
	{
		if (!PATTERN.accepts(parameters.getPosition()) && !PATTERN.accepts(parameters.getOriginalPosition()))
		{
			return;
		}

		C3ExprStmt lookupTarget = CompletionExtensionsKt.siblingOf(parameters, C3ExprStmt.class);
		if (lookupTarget == null) return;

		String lookupString = CompletionExtensionsKt.getLookupString(parameters, lookupTarget);
		FullyQualifiedName rootType = CompletionExtensionsKt.getRootType(lookupTarget);
		if (rootType == null) return;

		List<String> idents = List.of(lookupString.substring(lookupString.indexOf('.') + 1).split("\\."));
		List<Pair<AccessPath, String>> fields =
			StructService.INSTANCE.getFields(rootType, idents, parameters.getPosition().getProject());

		for (Pair<AccessPath, String> field : fields)
		{
			AccessPath accessPath = field.getFirst();
			if (accessPath.getSegments().size() != 1) continue;

			result.addElement(
				LookupElementBuilder.create(accessPath.getName())
					.withPresentableText(accessPath.getName())
					.withIcon(C3Icons.Nodes.STRUCT_FIELD)
					.withTypeText(field.getSecond())
			);
		}

		addMethods(lookupTarget, rootType, idents, result);
	}

	/** Adds the methods (type-bound functions/macros) declared on the accessed type. */
	private static void addMethods(
		@NotNull C3ExprStmt lookupTarget,
		@NotNull FullyQualifiedName rootType,
		@NotNull List<String> idents,
		@NotNull CompletionResultSet result)
	{
		var project = lookupTarget.getProject();
		String containerType =
			StructService.INSTANCE.resolveContainerType(rootType, idents, project);
		String typeSimpleName = FullyQualifiedName.parse(containerType).getName();
		String namePrefix = typeSimpleName + ".";

		C3ModuleDefinition module = lookupTarget.getModuleDefinition();

		Set<String> seen = new HashSet<>();
		for (C3FullyQualifiedNamePsiElement el : NameIndexService.INSTANCE.findByNamePrefix(namePrefix, project))
		{
			if (!(el instanceof C3CallablePsiElement callable) || callable.getType() == null) continue;
			if (!module.containsImportOrSameModule(el)) continue;

			String fullName = el.getFqName().getName();
			String methodName = fullName.substring(namePrefix.length());
			// Only direct methods of this type, not methods on a nested member type.
			if (methodName.isEmpty() || methodName.contains(".")) continue;
			if (!seen.add(methodName)) continue;

			result.addElement(methodLookupElement(callable, methodName));
		}

		// Interface methods are declared as bare func_defs inside the interface body, so they have
		// no `Type.` receiver and aren't found by the name-prefix lookup above. Add them when the
		// receiver's type is an interface (e.g. a `Formatter*` parameter).
		for (C3FuncDef method : C3Interfaces.interfaceMethods(typeSimpleName, project))
		{
			String methodName = C3Interfaces.methodSimpleName(method);
			if (methodName == null || methodName.contains(".") || !seen.add(methodName)) continue;

			result.addElement(methodLookupElement(method, methodName));
		}
	}

	/**
	 * Builds a method completion that inserts the call parentheses. The receiver is the method's
	 * first parameter, but with method-call syntax it is supplied implicitly, so the caret only
	 * belongs inside the parens when the method takes a further argument.
	 */
	private static @NotNull LookupElementBuilder methodLookupElement(
		@NotNull C3CallablePsiElement element,
		@NotNull String methodName)
	{
		boolean hasArguments = element.getParameterTypes().size() > 1;
		return LookupElementBuilder.create(element, methodName)
			.withPresentableText(methodName)
			.withIcon(AllIcons.Nodes.Method)
			.withTailText("()", true)
			.withInsertHandler(new MethodInsertHandler(hasArguments));
	}

	/** Appends "()" after a method name and positions the caret for the call. */
	private static final class MethodInsertHandler implements InsertHandler<LookupElement>
	{
		private final boolean hasArguments;

		private MethodInsertHandler(boolean hasArguments)
		{
			this.hasArguments = hasArguments;
		}

		@Override
		public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item)
		{
			var document = context.getDocument();
			int tailOffset = context.getTailOffset();

			// Append "()" unless the user already typed an opening paren.
			CharSequence chars = document.getCharsSequence();
			boolean hasOpeningParen = tailOffset < chars.length() && chars.charAt(tailOffset) == '(';
			if (!hasOpeningParen)
			{
				document.insertString(tailOffset, "()");
			}

			// Caret inside the parens when there is an argument to fill, after them otherwise.
			int caretOffset = (hasArguments || hasOpeningParen) ? tailOffset + 1 : tailOffset + 2;
			context.getEditor().getCaretModel().moveToOffset(caretOffset);
			context.commitDocument();

			if (hasArguments)
			{
				PsiElement element = item.getPsiElement();
				if (element != null)
				{
					AutoPopupController.getInstance(context.getProject())
						.autoPopupParameterInfo(context.getEditor(), element);
				}
			}
		}
	}
}
