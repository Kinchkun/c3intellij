package org.c3lang.intellij.psi.impl;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import org.c3lang.intellij.C3Icons;
import org.c3lang.intellij.psi.C3TypeName;
import org.c3lang.intellij.psi.C3Types;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.c3lang.intellij.psi.ModuleName;
import org.c3lang.intellij.stubs.C3TypeEnum;
import org.c3lang.intellij.stubs.C3TypeNameStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public abstract class C3TypeNameMixinImpl extends C3StubBasedPsiElementBase<C3TypeNameStub> implements C3TypeName
{
	public C3TypeNameMixinImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	public C3TypeNameMixinImpl(@NotNull C3TypeNameStub stub, @NotNull IStubElementType<?, ?> nodeType)
	{
		super(stub, nodeType);
	}

	public C3TypeNameMixinImpl(@NotNull C3TypeNameStub stub, @Nullable IElementType nodeType, @Nullable ASTNode node)
	{
		super(stub, nodeType, node);
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
	public @Nullable String getNameIdent()
	{
		LeafPsiElement ident = getNameIdentElement();
		return ident != null ? ident.getText() : null;
	}

	@Override
	public @Nullable LeafPsiElement getNameIdentElement()
	{
		PsiElement last = getLastChild();
		if (last != null && last.getNode().getElementType() == C3Types.TYPE_IDENT)
			return (LeafPsiElement) last;
		return null;
	}

	/** How the type reads in navigation popups: its name with its module as the location. */
	@Override
	public @NotNull ItemPresentation getPresentation()
	{
		FullyQualifiedName fqName = getFqName();
		ModuleName module = fqName.getModule();
		return new PresentationData(fqName.getName(), module != null ? module.getValue() : null, icon(getTypeEnum()), null);
	}

	private static @Nullable Icon icon(@NotNull C3TypeEnum typeEnum)
	{
		return switch (typeEnum)
		{
			case STRUCT -> C3Icons.Nodes.STRUCT;
			case UNION -> C3Icons.Nodes.UNION;
			case INTERFACE -> C3Icons.Nodes.INTERFACE;
			case ENUM -> C3Icons.Nodes.ENUM;
			case CONSTDEF -> C3Icons.Nodes.CONSTDEF;
			case BITSTRUCT -> C3Icons.Nodes.BITSTRUCT;
			case FAULT -> C3Icons.Nodes.FAULT;
			case FALLBACK -> null;
		};
	}

	@Override
	public @Nullable ModuleName getModuleName()
	{
		C3TypeNameStub s = getGreenStub();
		return s != null ? s.getModuleName() : ModuleName.Companion.from(this);
	}

	@Override
	public @NotNull FullyQualifiedName getFqName()
	{
		C3TypeNameStub s = getGreenStub();
		return s != null ? s.getFqName() : FullyQualifiedName.Companion.from(this, getModuleName());
	}

	@Override
	public @NotNull C3TypeEnum getTypeEnum()
	{
		C3TypeNameStub s = getGreenStub();
		return s != null ? s.getTypeEnum() : C3TypeEnum.find(this);
	}
}
