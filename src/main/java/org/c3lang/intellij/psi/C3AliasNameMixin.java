package org.c3lang.intellij.psi;

import com.intellij.psi.StubBasedPsiElement;
import org.c3lang.intellij.stubs.C3AliasNameStub;
import org.jetbrains.annotations.Nullable;

/**
 * The declared name of an {@code alias} ({@code mem} in {@code alias mem @builtin = thread_allocator;},
 * {@code MAX} in {@code alias MAX = LIMIT;}). A usage of the alias resolves to this element: it is
 * named (navigation, rename, Find Usages), its stub carries the fully qualified name so the name
 * index finds it from other files, and it knows the aliased value's type for member access.
 */
public interface C3AliasNameMixin extends C3FullyQualifiedNamePsiElement, StubBasedPsiElement<C3AliasNameStub>,
	C3NameIdentProvider, C3PsiNamedElement, C3FullyQualifiedTypeNameProvider
{
	/** The enclosing declaration, which holds the attributes and the aliased source. */
	@Nullable C3AliasDecl getDeclaration();
}
