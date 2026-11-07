package org.c3lang.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import org.c3lang.intellij.psi.*;
import org.c3lang.intellij.stubs.C3AliasNameStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class C3AliasNameMixinImpl extends C3StubBasedPsiElementBase<C3AliasNameStub> implements C3AliasName
{
	public C3AliasNameMixinImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	public C3AliasNameMixinImpl(@NotNull C3AliasNameStub stub, @NotNull IStubElementType<?, ?> nodeType)
	{
		super(stub, nodeType);
	}

	public C3AliasNameMixinImpl(@NotNull C3AliasNameStub stub, @Nullable IElementType nodeType, @Nullable ASTNode node)
	{
		super(stub, nodeType, node);
	}

	@Override
	public @Nullable String getName()
	{
		C3AliasNameStub stub = getGreenStub();
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

	/** The name token: an IDENT, CONST_IDENT or AT_IDENT, the element's only child. */
	@Override
	public @Nullable LeafPsiElement getNameIdentElement()
	{
		PsiElement first = getFirstChild();
		return first instanceof LeafPsiElement leaf ? leaf : null;
	}

	@Override
	public @Nullable C3AliasDecl getDeclaration()
	{
		return getParent() instanceof C3AliasDecl declaration ? declaration : null;
	}

	@Override
	public @NotNull FullyQualifiedName getFqName()
	{
		C3AliasNameStub stub = getGreenStub();
		if (stub != null) return stub.getName();
		String name = getNameIdent();
		return new FullyQualifiedName(ModuleName.from(this), name != null ? name : "");
	}

	/**
	 * The type of the aliased value when the alias names a variable ({@code alias mem = thread_allocator;}
	 * has the type of {@code thread_allocator}), so member access on the alias works; null for aliases
	 * of functions, macros, constants and modules.
	 */
	@Override
	public @Nullable FullyQualifiedName findTypeName()
	{
		C3AliasDecl declaration = getDeclaration();
		C3AliasDeclarationSource source = declaration != null ? declaration.getAliasDeclarationSource() : null;
		C3PathIdent aliased = source != null ? source.getPathIdent() : null;
		return aliased != null ? aliased.findTypeName() : null;
	}
}
