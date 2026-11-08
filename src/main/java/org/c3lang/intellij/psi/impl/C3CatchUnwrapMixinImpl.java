package org.c3lang.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.c3lang.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class C3CatchUnwrapMixinImpl extends C3PsiNamedElementImpl implements C3CatchUnwrap
{
	public C3CatchUnwrapMixinImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	@Override
	public @Nullable String getName()
	{
		return getNameIdent();
	}

	@Override
	public @NotNull PsiElement setName(@NotNull String name)
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
		LeafPsiElement ident = getNameIdentElement();
		return ident != null ? ident.getTextOffset() : super.getTextOffset();
	}

	@Override
	public @Nullable String getNameIdent()
	{
		LeafPsiElement ident = getNameIdentElement();
		return ident != null ? ident.getText() : null;
	}

	/** The declared name: the IDENT token of this declaration, if it has one. */
	@Override
	public @Nullable LeafPsiElement getNameIdentElement()
	{
		ASTNode ident = getNode().findChildByType(C3Types.IDENT);
		return ident != null ? (LeafPsiElement) ident.getPsi() : null;
	}

	/** The written type, resolved to its declaring module; null when the type is inferred. */
	@Override
	public @Nullable FullyQualifiedName findTypeName()
	{
		C3Type type = getType();
		return type != null ? getModuleDefinition().resolveDeclaringModule(FullyQualifiedName.Companion.from(type)) : null;
	}
}
