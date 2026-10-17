package org.c3lang.intellij.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.c3lang.intellij.psi.C3PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Base implementation for ordinary (non-stub) C3 PSI elements.
 *
 * <p>{@link ASTWrapperPsiElement} is the platform's standard PSI element: it wraps a single
 * {@link ASTNode} and provides tree navigation, text ranges, etc. Generated {@code C3*Impl} classes
 * for grammar rules <em>without</em> a {@code mixin(...)} directive extend this directly; rules
 * <em>with</em> a mixin extend their hand-written {@code C3*MixinImpl}, which in turn extends this.
 * That is the hook that lets us add behaviour to generated code (see the mixin pattern in HowTo.adoc).
 */
public class C3PsiElementImpl extends ASTWrapperPsiElement implements C3PsiElement {
    public C3PsiElementImpl(@NotNull ASTNode node) {
        super(node);
    }
}
