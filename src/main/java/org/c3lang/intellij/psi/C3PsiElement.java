package org.c3lang.intellij.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The common super-interface of every C3 PSI element.
 *
 * <p>Giving all our elements one shared interface lets the rest of the plugin accept "any C3 node"
 * and hang shared helpers off it. The {@link #getModuleDefinition} default method, for example, walks
 * up the tree to the enclosing {@code module ...;} declaration – useful all over resolution and
 * completion, where visibility depends on which module a symbol lives in.
 *
 * <p>Concrete implementations come from the generated {@code C3*Impl} classes, which ultimately
 * extend {@link org.c3lang.intellij.psi.impl.C3PsiElementImpl} (see the mixin pattern).
 */
public interface C3PsiElement extends PsiElement {

    /** The {@code module ...;} declaration this element belongs to (never null for a valid tree). */
    default @NotNull C3ModuleDefinition getModuleDefinition() {
        return Objects.requireNonNull(PsiTreeUtil.getParentOfType(this, C3ModuleDefinition.class));
    }
}
