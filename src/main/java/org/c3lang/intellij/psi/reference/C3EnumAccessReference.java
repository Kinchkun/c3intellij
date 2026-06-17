package org.c3lang.intellij.psi.reference;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3BaseType;
import org.c3lang.intellij.psi.C3ConstdefConstant;
import org.c3lang.intellij.psi.C3EnumAccessExpr;
import org.c3lang.intellij.psi.C3EnumConstant;
import org.c3lang.intellij.psi.C3Types;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the constant in a {@code Type.CONSTANT} access (parsed as an enum access
 * expression) to its declaration — an enum constant or a constdef constant — so
 * Go to Declaration and Quick Documentation work on it.
 */
public final class C3EnumAccessReference extends PsiReferenceBase<C3EnumAccessExpr>
{
	public C3EnumAccessReference(@NotNull C3EnumAccessExpr element)
	{
		super(element, rangeOf(element));
	}

	@Override
	public @Nullable PsiElement resolve()
	{
		C3BaseType baseType = myElement.getBaseType();
		PsiReference baseReference = baseType.getReference();
		PsiElement typeName = baseReference != null ? baseReference.resolve() : null;
		if (typeName == null) return null;

		// typeName is the C3TypeName of the enum/constdef; its parent is the declaration.
		PsiElement declaration = typeName.getParent();
		if (declaration == null) return null;

		String name = constName(myElement);
		if (name == null) return null;

		for (C3ConstdefConstant constant : PsiTreeUtil.findChildrenOfType(declaration, C3ConstdefConstant.class))
		{
			if (name.equals(constant.getName())) return constant;
		}
		for (C3EnumConstant constant : PsiTreeUtil.findChildrenOfType(declaration, C3EnumConstant.class))
		{
			if (name.equals(constant.getName())) return constant;
		}
		return null;
	}

	private static @NotNull TextRange rangeOf(@NotNull C3EnumAccessExpr element)
	{
		ASTNode constIdent = element.getNode().findChildByType(C3Types.CONST_IDENT);
		if (constIdent == null) return TextRange.from(0, element.getTextLength());
		int start = constIdent.getStartOffset() - element.getTextRange().getStartOffset();
		return TextRange.from(start, constIdent.getTextLength());
	}

	private static @Nullable String constName(@NotNull C3EnumAccessExpr element)
	{
		ASTNode constIdent = element.getNode().findChildByType(C3Types.CONST_IDENT);
		return constIdent != null ? constIdent.getText() : null;
	}
}
