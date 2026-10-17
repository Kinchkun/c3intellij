package org.c3lang.intellij.psi;

import org.jetbrains.annotations.Nullable;

public interface C3LocalDeclAfterTypeMixin extends C3PsiNamedElement, C3NameIdentProvider, C3FullyQualifiedTypeNameProvider
{
	/**
	 * The declared type of this variable, or null if it cannot be determined. The type is not a child
	 * of this element: it lives on the enclosing {@code local_declaration_stmt} for a statement
	 * declaration, and as a sibling in {@code decl_or_expr} for one inside a {@code for}/{@code if}
	 * condition.
	 */
	@Nullable C3OptionalType getDeclaredType();
}
