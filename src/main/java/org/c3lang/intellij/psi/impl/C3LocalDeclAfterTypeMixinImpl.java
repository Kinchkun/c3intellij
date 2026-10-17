package org.c3lang.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.c3lang.intellij.psi.C3DeclOrExpr;
import org.c3lang.intellij.psi.C3DeclStmtAfterType;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3LocalDeclarationStmt;
import org.c3lang.intellij.psi.C3OptionalType;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class C3LocalDeclAfterTypeMixinImpl extends C3PsiNamedElementImpl implements C3LocalDeclAfterType
{
	public C3LocalDeclAfterTypeMixinImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	@Override
	public @Nullable String getName()
	{
		return getNameIdent();
	}

	@Override
	public @Nullable PsiElement setName(@NotNull String name)
	{
		LeafPsiElement ident = getNameIdentElement();
		if (ident != null) ident.replaceWithText(name);
		return this;
	}

	@Override
	public @Nullable PsiElement getNameIdentifier()
	{
		return getNameIdentElement();
	}

	@Override
	public int getTextOffset()
	{
		PsiElement ident = getNameIdentifier();
		return ident != null ? ident.getTextOffset() : super.getTextOffset();
	}

	@Override
	public @Nullable LeafPsiElement getNameIdentElement()
	{
		PsiElement first = getFirstChild();
		return first instanceof LeafPsiElement ? (LeafPsiElement) first : null;
	}

	@Override
	public @Nullable String getNameIdent()
	{
		LeafPsiElement ident = getNameIdentElement();
		return ident != null ? ident.getText() : null;
	}

	@Override
	public @Nullable C3OptionalType getDeclaredType()
	{
		PsiElement parent = getParent();
		// `int a, b;` -- the type belongs to the enclosing local_declaration_stmt.
		if (parent instanceof C3DeclStmtAfterType)
		{
			PsiElement stmt = parent.getParent();
			return stmt instanceof C3LocalDeclarationStmt ? ((C3LocalDeclarationStmt) stmt).getOptionalType() : null;
		}
		// `for (int i = 0; ...)`, `if (int x = f())` -- the type is a sibling inside decl_or_expr.
		if (parent instanceof C3DeclOrExpr) return ((C3DeclOrExpr) parent).getOptionalType();
		return null;
	}

	@Override
	public @Nullable FullyQualifiedName findTypeName()
	{
		C3OptionalType type = getDeclaredType();
		return type != null ? type.getModuleDefinition().resolveDeclaringModule(FullyQualifiedName.from(type)) : null;
	}
}
