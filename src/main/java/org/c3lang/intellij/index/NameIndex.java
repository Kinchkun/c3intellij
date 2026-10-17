package org.c3lang.intellij.index;

import com.intellij.psi.stubs.StubIndexKey;
import org.c3lang.intellij.psi.C3PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * The project-wide index mapping a declaration's fully-qualified name → its PSI element.
 *
 * <p>A stub index is a persistent multimap maintained by the platform: as files are indexed, each
 * stub's {@code indexStub(...)} calls {@code sink.occurrence(KEY, ...)} to add entries (see
 * {@code stubs/C3FuncDefElementType}). Querying it (via {@link NameIndexService}) returns matching
 * declarations from anywhere in the project without parsing their files – this is what makes
 * go-to-declaration and completion fast across modules.
 *
 * <p>Registered in {@code plugin.xml} via {@code <stubIndex implementation="...NameIndex"/>}. The
 * {@link #KEY} string {@code "c3.name"} is the index's unique id.
 */
public class NameIndex extends C3StringStubIndexExtension<C3PsiElement>
{
	public static final StubIndexKey<String, C3PsiElement> KEY = StubIndexKey.createIndexKey("c3.name");

	@Override
	public @NotNull StubIndexKey<String, C3PsiElement> getKey()
	{
		return KEY;
	}
}
