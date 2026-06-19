package org.c3lang.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
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

		boolean added = false;
		for (C3EnumConstant constant : PsiTreeUtil.findChildrenOfType(declaration, C3EnumConstant.class))
		{
			added |= addConstant(result, constant);
		}
		for (C3ConstdefConstant constant : PsiTreeUtil.findChildrenOfType(declaration, C3ConstdefConstant.class))
		{
			added |= addConstant(result, constant);
		}

		// `EnumType.` is a closed position — nothing else (types, values) belongs here. Stop the chain
		// so the dummy-induced "looks like a type" parse doesn't also pull in type completions.
		if (added) result.stopHere();
	}

	private static boolean addConstant(@NotNull CompletionResultSet result, @NotNull PsiNamedElement constant)
	{
		String name = constant.getName();
		if (name == null || name.isEmpty()) return false;
		result.addElement(LookupElementBuilder.create(constant, name).withIcon(C3Icons.Nodes.ENUM));
		return true;
	}
}
