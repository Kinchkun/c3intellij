package org.c3lang.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import org.c3lang.intellij.psi.*;
import org.c3lang.intellij.stubs.C3GlobalNameStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class C3GlobalNameMixinImpl extends C3StubBasedPsiElementBase<C3GlobalNameStub> implements C3GlobalName
{
	public C3GlobalNameMixinImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	public C3GlobalNameMixinImpl(@NotNull C3GlobalNameStub stub, @NotNull IStubElementType<?, ?> nodeType)
	{
		super(stub, nodeType);
	}

	public C3GlobalNameMixinImpl(@NotNull C3GlobalNameStub stub, @Nullable IElementType nodeType, @Nullable ASTNode node)
	{
		super(stub, nodeType, node);
	}

	@Override
	public @Nullable String getName()
	{
		C3GlobalNameStub stub = getGreenStub();
		return stub != null ? stub.getName().getName() : getNameIdent();
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

	@Override
	public @Nullable LeafPsiElement getNameIdentElement()
	{
		PsiElement first = getFirstChild();
		return first instanceof LeafPsiElement leaf && leaf.getElementType() == C3Types.IDENT ? leaf : null;
	}

	@Override
	public @Nullable C3GlobalDecl getDeclaration()
	{
		return getParent() instanceof C3GlobalDecl declaration ? declaration : null;
	}

	@Override
	public @NotNull FullyQualifiedName getFqName()
	{
		C3GlobalNameStub stub = getGreenStub();
		if (stub != null) return stub.getName();
		String name = getNameIdent();
		return new FullyQualifiedName(ModuleName.from(this), name != null ? name : "");
	}

	/** The declared type, resolved to the module that declares it, so member access on the global works. */
	@Override
	public @Nullable FullyQualifiedName findTypeName()
	{
		C3GlobalDecl declaration = getDeclaration();
		if (declaration == null) return null;
		return getModuleDefinition().resolveDeclaringModule(FullyQualifiedName.Companion.from(declaration.getOptionalType()));
	}
}
