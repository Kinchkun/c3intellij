package org.c3lang.intellij.index;

import com.intellij.psi.stubs.StubIndexKey;
import org.c3lang.intellij.psi.C3TypeName;
import org.jetbrains.annotations.NotNull;

/**
 * Maps the simple name of an interface to the types that list it: {@code struct Foo (Printable)}
 * and {@code interface Loud : Printable} are both stored under {@code Printable}.
 *
 * <p>The key is the name as written, without its module path, because a stub is built from one
 * file's text and cannot resolve the name. {@code C3Implementations} resolves the candidates.</p>
 */
public class InterfaceImplementationIndex extends C3StringStubIndexExtension<C3TypeName>
{
	public static final StubIndexKey<String, C3TypeName> KEY = StubIndexKey.createIndexKey("c3.interface.implementation");

	@Override
	public @NotNull StubIndexKey<String, C3TypeName> getKey()
	{
		return KEY;
	}
}
