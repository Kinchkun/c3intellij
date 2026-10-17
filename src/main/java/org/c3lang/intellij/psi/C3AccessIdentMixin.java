package org.c3lang.intellij.psi;

import org.jetbrains.annotations.Nullable;

public interface C3AccessIdentMixin extends C3PsiNamedElement, C3NameIdentProvider, C3FullyQualifiedTypeNameProvider
{
	/**
	 * The type of the <em>immediate</em> receiver of this member access — i.e. the type of the
	 * expression directly to the left of the dot. For {@code object.foo} this is the type of
	 * {@code object}; for {@code a.b.foo} it is the type of {@code a.b}.
	 *
	 * <p>Returns {@code null} when that type cannot be determined (unknown variable, a receiver whose
	 * own member did not resolve, a non-dotted access, etc.). Callers use a {@code null} result as
	 * "don't know — make no claims", which is what keeps the unresolved-member inspection free of
	 * false positives.
	 */
	@Nullable FullyQualifiedName findReceiverType();
}
