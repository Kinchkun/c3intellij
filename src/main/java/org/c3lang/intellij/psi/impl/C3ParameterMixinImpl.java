package org.c3lang.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.c3lang.intellij.psi.C3Types;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3Parameter;
import org.c3lang.intellij.psi.C3Type;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class C3ParameterMixinImpl extends C3PsiNamedElementImpl implements C3Parameter
{
	public C3ParameterMixinImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	@Override
	public @Nullable PsiElement getNameIdentifier()
	{
		return getNameIdentElement();
	}

	@Override
	public @Nullable String getName()
	{
		PsiElement ident = getNameIdentifier();
		return ident != null ? ident.getText() : null;
	}

	@Override
	public @Nullable PsiElement setName(@NotNull String name)
	{
		LeafPsiElement ident = getNameIdentElement();
		if (ident != null) ident.replaceWithText(name);
		return this;
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
		// The name token is not always the last child: `args...` ends in the ellipsis and
		// `int x @unused` in an attribute.
		for (PsiElement child = getLastChild(); child != null; child = child.getPrevSibling())
		{
			IElementType type = child.getNode().getElementType();
			if (type == C3Types.IDENT || type == C3Types.HASH_IDENT || type == C3Types.CT_IDENT) return (LeafPsiElement) child;
		}
		PsiElement last = getLastChild();
		return last instanceof LeafPsiElement ? (LeafPsiElement) last : null;
	}

	@Override
	public @Nullable FullyQualifiedName findTypeName()
	{
		C3Type type = getType();
		if (type != null) return getModuleDefinition().resolveDeclaringModule(FullyQualifiedName.Companion.from(type));

		// An implicit method receiver (`&self`/`self`) carries no written type; its type is the
		// receiver type of the enclosing method, i.e. `Receiver` in `fn ... Receiver.name(&self, ...)`.
		if ("self".equals(getName()))
		{
			C3FuncDef funcDef = PsiTreeUtil.getParentOfType(this, C3FuncDef.class);
			C3Type receiver = funcDef != null ? funcDef.getFuncHeader().getFuncName().getType() : null;
			if (receiver != null) return getModuleDefinition().resolveDeclaringModule(FullyQualifiedName.Companion.from(receiver));
		}
		return null;
	}
}
