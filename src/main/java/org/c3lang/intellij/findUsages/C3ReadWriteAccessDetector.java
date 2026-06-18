package org.c3lang.intellij.findUsages;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3BinaryExpr;
import org.c3lang.intellij.psi.C3GlobalDecl;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3Parameter;
import org.c3lang.intellij.psi.C3PathIdentExpr;
import org.c3lang.intellij.psi.C3StructMemberDeclaration;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Splits Find Usages (and in-editor highlight) of C3 variables, parameters, globals and fields
 * into read and write accesses. A simple-identifier reference that is the whole left-hand side of
 * an assignment is a write ({@code =}) or read-write (compound assignments like {@code +=}).
 */
public final class C3ReadWriteAccessDetector extends ReadWriteAccessDetector
{
	private static final Set<String> ASSIGN_OPS =
		Set.of("=", "+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "|=", "^=", "~=");

	@Override
	public boolean isReadWriteAccessible(@NotNull PsiElement element)
	{
		return element instanceof C3LocalDeclAfterType
			|| element instanceof C3Parameter
			|| element instanceof C3GlobalDecl
			|| element instanceof C3StructMemberDeclaration;
	}

	@Override
	public boolean isDeclarationWriteAccess(@NotNull PsiElement element)
	{
		return false;
	}

	@Override
	public @NotNull Access getReferenceAccess(@NotNull PsiElement referencedElement, @NotNull PsiReference reference)
	{
		return getExpressionAccess(reference.getElement());
	}

	@Override
	public @NotNull Access getExpressionAccess(@NotNull PsiElement expression)
	{
		C3PathIdentExpr expr = expression instanceof C3PathIdentExpr e
			? e
			: PsiTreeUtil.getParentOfType(expression, C3PathIdentExpr.class, false);
		if (expr == null) return Access.Read;

		if (expr.getParent() instanceof C3BinaryExpr binary && binary.getLeft() == expr)
		{
			String operator = operatorText(binary);
			if (operator != null && ASSIGN_OPS.contains(operator))
			{
				return operator.equals("=") ? Access.Write : Access.ReadWrite;
			}
		}
		return Access.Read;
	}

	/** The operator token between the two operands of a binary expression, or null. */
	private static String operatorText(@NotNull C3BinaryExpr binary)
	{
		PsiElement node = binary.getLeft().getNextSibling();
		while (node instanceof PsiWhiteSpace || node instanceof PsiComment)
		{
			node = node.getNextSibling();
		}
		return node != null ? node.getText() : null;
	}
}
