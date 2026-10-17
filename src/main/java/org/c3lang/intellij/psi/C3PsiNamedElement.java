package org.c3lang.intellij.psi;

import com.intellij.psi.PsiNameIdentifierOwner;

/**
 * A C3 PSI element that <em>declares a name</em> – functions, types, variables, parameters, etc.
 *
 * <p>{@link PsiNameIdentifierOwner} is the platform contract for "this element has a name and an
 * identifier token you can rename". Implementing it is what makes generic features work out of the
 * box: rename refactoring, the structure view, go-to-symbol, and find-usages all key off
 * {@code getName()}/{@code getNameIdentifier()}. The actual rename edit is performed by
 * {@link NamedElementManipulator}.
 */
public interface C3PsiNamedElement extends C3PsiElement, PsiNameIdentifierOwner
{
}
