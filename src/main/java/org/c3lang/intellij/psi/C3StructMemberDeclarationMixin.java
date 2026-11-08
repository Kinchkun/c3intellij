package org.c3lang.intellij.psi;

import com.intellij.psi.StubBasedPsiElement;
import org.c3lang.intellij.stubs.C3StructMemberDeclarationStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
	 * The struct paths this declaration introduces, one per declared name: {@code int w, h;}
	 * yields {@code w} and {@code h}, a nested {@code inner.w, h} yields {@code inner.w} and
	 * {@code inner.h}. {@link #getStructPath()} keeps the names joined as written.
	 */
	default @NotNull List<String> getMemberPaths()
	{
		return C3StructMemberDeclarationStub.memberPaths(getStructPath());
	}

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
