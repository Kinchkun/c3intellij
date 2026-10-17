package org.c3lang.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import org.c3lang.intellij.psi.C3PsiNamedElement;
import org.jetbrains.annotations.NotNull;

/**
 * Base implementation for <em>named</em> C3 elements that are not stub-backed.
 *
 * <p>Adds the {@link C3PsiNamedElement} marker on top of {@link C3PsiElementImpl}. Concrete mixin
 * impls (e.g. {@code C3PathMixinImpl}) extend this and supply the real {@code getName()}/
 * {@code setName()}/{@code getNameIdentifier()} logic against the underlying AST. Stub-backed named
 * elements (functions, types...) instead extend {@link C3StubBasedPsiElementBase}.
 */
public abstract class C3PsiNamedElementImpl extends C3PsiElementImpl implements C3PsiNamedElement
{
	public C3PsiNamedElementImpl(@NotNull ASTNode node) { super(node); }
}
