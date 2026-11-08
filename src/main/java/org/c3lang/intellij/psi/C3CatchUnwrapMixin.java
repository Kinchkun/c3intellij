package org.c3lang.intellij.psi;

/** The error variable of {@code if (catch err = expr)}: a named local declaration that identifiers in its scope resolve to. */
public interface C3CatchUnwrapMixin extends C3PsiNamedElement, C3NameIdentProvider, C3FullyQualifiedTypeNameProvider
{
}
