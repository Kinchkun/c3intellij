package org.c3lang.intellij.psi.reference;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.c3lang.intellij.psi.C3EnumAccessExpr;
import org.c3lang.intellij.psi.C3InterfaceImpl;
import org.c3lang.intellij.psi.C3TypeName;
import org.jetbrains.annotations.NotNull;

/**
 * Adds references that the generated PSI doesn't expose, without needing a grammar mixin
 * (and therefore without requiring parser regeneration).
 */
public final class C3ReferenceContributor extends PsiReferenceContributor
{
	@Override
	public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar)
	{
		registrar.registerReferenceProvider(
			PlatformPatterns.psiElement(C3EnumAccessExpr.class),
			new PsiReferenceProvider()
			{
				@Override
				public PsiReference @NotNull [] getReferencesByElement(
					@NotNull PsiElement element, @NotNull ProcessingContext context)
				{
					if (!(element instanceof C3EnumAccessExpr enumAccess)) return PsiReference.EMPTY_ARRAY;
					return new PsiReference[]{ new C3EnumAccessReference(enumAccess) };
				}
			});

		// The interface name in `struct X (Interface)` is a C3TypeName with no built-in reference.
		registrar.registerReferenceProvider(
			PlatformPatterns.psiElement(C3TypeName.class).withParent(C3InterfaceImpl.class),
			new PsiReferenceProvider()
			{
				@Override
				public PsiReference @NotNull [] getReferencesByElement(
					@NotNull PsiElement element, @NotNull ProcessingContext context)
				{
					if (!(element instanceof C3TypeName typeName)) return PsiReference.EMPTY_ARRAY;
					return new PsiReference[]{ new C3InterfaceNameReference(typeName) };
				}
			});
	}
}
