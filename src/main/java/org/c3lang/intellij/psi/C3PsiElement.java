package org.c3lang.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


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
        C3ModuleDefinition module = findModuleDefinition();
        if (module == null) {
            PsiFile file = getContainingFile();
            throw new IllegalStateException("No module section around " + this + " in " + (file != null ? file.getName() : "a detached tree"));
        }
        return module;
    }

    /**
     * The module section this element belongs to, or null when the tree has none (an empty or
     * detached fragment).
     *
     * <p>The walk follows the AST, not {@code getParent()}: stub-backed declarations (functions,
     * macros, types...) navigate their parents through the stub tree when a stub is attached, and
     * the module sections are not stub elements, so that walk skips them and lands on the file. A
     * file loaded from the index and parsed later, as during a search over files that are not
     * open, is in exactly that state.</p>
     */
    default @Nullable C3ModuleDefinition findModuleDefinition() {
        return findModuleDefinition(this);
    }

    /** {@link #findModuleDefinition()} for any element of a C3 file, leaf tokens included. */
    static @Nullable C3ModuleDefinition findModuleDefinition(@NotNull PsiElement element) {
        for (ASTNode node = element.getNode(); node != null; node = node.getTreeParent()) {
            PsiElement psi = node.getPsi();
            if (psi instanceof C3ModuleDefinition module) return module;
            if (psi instanceof PsiFile) break;
        }
        PsiFile file = element.getContainingFile();
        return file != null ? PsiTreeUtil.findChildOfType(file, C3ModuleDefinition.class) : null;
    }
}
