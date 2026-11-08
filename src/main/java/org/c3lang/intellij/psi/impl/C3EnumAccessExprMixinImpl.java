package org.c3lang.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import org.c3lang.intellij.psi.C3EnumAccessExpr;
import org.c3lang.intellij.psi.reference.C3EnumAccessReference;
import org.jetbrains.annotations.NotNull;

/**
 * Attaches the constant reference of a {@code Type.CONSTANT} access ({@code Keycode.KEY_LEFT},
 * {@code sdl::Keycode.KEY_LEFT}) to the element itself. A reference added through a
 * {@code psi.referenceContributor} is not returned by {@code getReferences()} of a plain C3
 * element, so Go to Declaration never saw it and reported "Cannot find declaration to go to".
 */
public abstract class C3EnumAccessExprMixinImpl extends C3PsiElementImpl implements C3EnumAccessExpr
{
	public C3EnumAccessExprMixinImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	@Override
	public @NotNull PsiReference getReference()
	{
		return new C3EnumAccessReference(this);
	}
}
