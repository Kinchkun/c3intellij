package org.c3lang.intellij.psi;

import com.intellij.psi.StubBasedPsiElement;
import org.c3lang.intellij.stubs.C3StructMemberDeclarationStub;
import org.jetbrains.annotations.Nullable;

public interface C3StructMemberDeclarationMixin
	extends StubBasedPsiElement<C3StructMemberDeclarationStub>,
		C3PsiNamedElement,
		C3DeclaredInPathProvider,
		C3DeclaredInProvider,
		C3NameIdentProvider,
		C3FullyQualifiedTypeNameProvider
{
	@Nullable FullyQualifiedName getStructType();
	@Nullable String getStructPath();
	@Nullable FullyQualifiedName getStructPathType();

	/**
	 * The member's type, resolved to the module that declares it. {@link #getStructPathType()} is
	 * computed while indexing, so it places an unqualified type (`List { String } lines;`) in the
	 * member's own module; this looks the type up through that file's imports.
	 */
	@Override
	default @Nullable FullyQualifiedName findTypeName()
	{
		return getModuleDefinition().resolveDeclaringModule(getStructPathType());
	}
}
