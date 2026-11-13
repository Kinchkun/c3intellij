package org.c3lang.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.codeInsight.lookup.WeighingContext;
import com.intellij.openapi.util.Key;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.c3lang.intellij.C3Icons;
import org.c3lang.intellij.psi.C3BaseType;
import org.c3lang.intellij.psi.C3ConstdefConstant;
import org.c3lang.intellij.psi.C3EnumConstant;
import org.c3lang.intellij.psi.C3Types;
import org.jetbrains.annotations.NotNull;

/**
 * Completes the constants of an enum (or constdef) after {@code EnumType.}, e.g. offering
 * {@code WRITEDATA}/{@code URL} for {@code CurlOption.}.
 *
 * <p>The completion dummy makes the member after the dot parse inconsistently (an empty prefix yields
 * a {@code C3CallExpr}/{@code C3AccessIdent}; an upper-case prefix turns it into a stray
 * {@code C3BaseType}), so this scans backwards from the caret to the {@code .} and the receiver type
 * instead of relying on the member's own (broken) parse.</p>
 */
public final class EnumAccessCompletionContributor extends CompletionProvider<CompletionParameters>
{
	public static final EnumAccessCompletionContributor INSTANCE = new EnumAccessCompletionContributor();

	private EnumAccessCompletionContributor()
	{
	}

	@Override
	protected void addCompletions(
		@NotNull CompletionParameters parameters,
		@NotNull ProcessingContext context,
		@NotNull CompletionResultSet result)
	{
		// The token immediately left of the member being typed must be the access dot.
		PsiElement dot = PsiTreeUtil.prevVisibleLeaf(parameters.getPosition());
		if (dot == null || dot.getNode().getElementType() != C3Types.DOT) return;

		// And the token before the dot must be (part of) a type name, i.e. `Type.` — not `value.`,
		// which is ordinary member access handled elsewhere.
		PsiElement beforeDot = PsiTreeUtil.prevVisibleLeaf(dot);
		C3BaseType receiver = PsiTreeUtil.getParentOfType(beforeDot, C3BaseType.class, false);
		if (receiver == null) return;

		PsiReference reference = receiver.getReference();
		PsiElement resolved = reference != null ? reference.resolve() : null;
		if (resolved == null) return;

		// The reference resolves to the type's C3TypeName; its parent is the declaration that owns the
		// enum/constdef constants (mirrors how C3EnumAccessReference resolves a single one).
		PsiElement declaration = resolved.getParent();
		if (declaration == null) return;

		// Constants that start with what was typed come first, each group in declaration order:
		// `CurlResult.O` offers `OK` (the first constant) before the `OBSOLETE…` entries, and
		// `ABORTED_BY_CALLBACK`, which only contains an `O`, after them.
		CompletionResultSet ordered = result.withRelevanceSorter(DECLARATION_ORDER);
		int index = 0;
		for (PsiNamedElement constant : PsiTreeUtil.findChildrenOfAnyType(declaration, C3EnumConstant.class, C3ConstdefConstant.class))
		{
			String name = constant.getName();
			if (name == null || name.isEmpty()) continue;
			LookupElement element = LookupElementBuilder.create(constant, name).withIcon(C3Icons.Nodes.ENUM);
			element.putUserData(DECLARATION_INDEX, index++);
			ordered.addElement(element);
		}

		// `EnumType.` is a closed position — nothing else (types, values) belongs here. Stop the chain
		// so the dummy-induced "looks like a type" parse doesn't also pull in type completions.
		if (index > 0) result.stopHere();
	}

	private static final Key<Integer> DECLARATION_INDEX = Key.create("c3.enum.constant.index");

	private static final CompletionSorter DECLARATION_ORDER = CompletionSorter.emptySorter()
		.weigh(new LookupElementWeigher("c3EnumStartMatch", false, true)
		{
			@Override
			public @NotNull Comparable<?> weigh(@NotNull LookupElement element, @NotNull WeighingContext context)
			{
				String typed = context.itemPattern(element);
				return element.getLookupString().regionMatches(true, 0, typed, 0, typed.length()) ? 0 : 1;
			}
		})
		.weigh(new LookupElementWeigher("c3EnumDeclarationOrder")
		{
			@Override
			public @NotNull Comparable<?> weigh(@NotNull LookupElement element)
			{
				Integer index = element.getUserData(DECLARATION_INDEX);
				return index != null ? index : Integer.MAX_VALUE;
			}
		});
}
