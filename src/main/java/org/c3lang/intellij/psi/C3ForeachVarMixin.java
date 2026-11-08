package org.c3lang.intellij.psi;

/** The loop variable of a foreach ({@code foreach (row, line : lines)}): a named local declaration that identifiers in its scope resolve to. */
public interface C3ForeachVarMixin extends C3PsiNamedElement, C3NameIdentProvider, C3FullyQualifiedTypeNameProvider
{
}
