package org.c3lang.intellij.psi.reference;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.c3lang.intellij.psi.C3EnumAccessExpr;
import org.jetbrains.annotations.NotNull;

/**
 * Adds references that the generated PSI doesn't expose, without needing a grammar mixin
 * (and therefore without requiring parser regeneration).
 *
 * <p>This is the alternative to attaching a reference via a {@code *MixinImpl.getReference()}: instead
 * of changing the grammar, we register a {@link PsiReferenceProvider} against a PSI pattern. The
 * platform calls our provider for every element matching the pattern and we hand back the reference.
 *
 * <p>Registered in {@code plugin.xml} via {@code <psi.referenceContributor language="C3" .../>}. To
 * make a new kind of element navigable this way, add another {@code registerReferenceProvider(...)}
 * call below with the element pattern and the {@link C3ReferenceBase} subclass to attach.
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
	}
}
