package org.c3lang.intellij.psi;

/**
 * A declaration that may carry {@code @builtin}: such a name is visible everywhere without a
 * module path ({@code abort(...)}, not {@code builtin::abort(...)}), so completion inserts it bare.
 */
public interface C3BuiltinPsiElement
{
	boolean isBuiltin();
}
