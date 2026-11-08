package org.c3lang.intellij.psi;

/** The value variable of {@code if (try value = expr)}: a named local declaration that identifiers in its scope resolve to. */
public interface C3TryUnwrapMixin extends C3PsiNamedElement, C3NameIdentProvider, C3FullyQualifiedTypeNameProvider
{
}
