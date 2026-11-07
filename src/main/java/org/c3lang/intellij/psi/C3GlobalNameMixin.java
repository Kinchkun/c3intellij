package org.c3lang.intellij.psi;

import com.intellij.psi.StubBasedPsiElement;
import org.c3lang.intellij.stubs.C3GlobalNameStub;
import org.jetbrains.annotations.Nullable;

/**
 * One name declared by a module-level {@code global_decl}: {@code editor} in {@code Editor editor;},
 * or each of {@code a} and {@code b} in {@code int a, b;}. A usage of the global resolves to this
 * element: it is named (navigation, rename, Find Usages), its stub carries the fully qualified name
 * so the name index finds it from other files, and it knows the declared type for member access.
 */
public interface C3GlobalNameMixin extends C3FullyQualifiedNamePsiElement, StubBasedPsiElement<C3GlobalNameStub>,
	C3NameIdentProvider, C3PsiNamedElement, C3FullyQualifiedTypeNameProvider
{
	/** The enclosing declaration, which holds the type, the attributes and the initializer. */
	@Nullable C3GlobalDecl getDeclaration();
}
