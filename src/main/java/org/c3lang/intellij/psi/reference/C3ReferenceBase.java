package org.c3lang.intellij.psi.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.c3lang.intellij.psi.C3NameIdentProvider;
import org.c3lang.intellij.psi.C3PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Shared base for all C3 references. Concrete subclasses only implement {@link #multiResolve()}.
 *
 * <p>{@link PsiPolyVariantReferenceBase} provides the boilerplate (text range, caching hooks...). This
 * base bridges the platform's {@code ResolveResult[]} world to our simpler {@link #multiResolve()}
 * that returns {@link C3PsiElement}s, narrows {@link #resolve()} to a C3 type, and routes renames
 * through {@link #doRename} so the same identifier-rewrite logic is reused everywhere.
 *
 * @param <T> the type of usage-site element this reference is attached to
 */
public abstract class C3ReferenceBase<T extends C3PsiElement> extends PsiPolyVariantReferenceBase<T>
	implements C3Reference
{
	public C3ReferenceBase(@NotNull T element)
	{
		super(element);
	}

	@Override
	public @Nullable C3PsiElement resolve()
	{
		// Reduce the polyvariant results to a single best match (the platform picks the first).
		PsiElement resolved = super.resolve();
		return resolved instanceof C3PsiElement ? (C3PsiElement) resolved : null;
	}

	@Override
	public ResolveResult @NotNull [] multiResolve(boolean incompleteCode)
	{
		// Adapt our C3PsiElement results to the platform's ResolveResult[] contract.
		Collection<C3PsiElement> results = multiResolve();
		ResolveResult[] array = new ResolveResult[results.size()];
		int i = 0;
		for (C3PsiElement element : results)
		{
			array[i++] = new PsiElementResolveResult(element);
		}
		return array;
	}

	@Override
	public @NotNull PsiElement handleElementRename(@NotNull String newName)
	{
		doRename(myElement, newName);
		return myElement;
	}

	@Override
	public LookupElement @NotNull [] getVariants()
	{
		return LookupElement.EMPTY_ARRAY;
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof C3ReferenceBase && myElement.equals(((C3ReferenceBase<?>) other).myElement);
	}

	@Override
	public int hashCode()
	{
		return myElement.hashCode();
	}

	/** Rewrites an identifier's text during a rename, handling both raw leaf tokens and named elements. */
	public static void doRename(@NotNull PsiElement element, @NotNull String newName)
	{
		if (element instanceof LeafPsiElement)
		{
			((LeafPsiElement) element).replaceWithText(newName);
		}
		else if (element instanceof C3NameIdentProvider)
		{
			LeafPsiElement ident = ((C3NameIdentProvider) element).getNameIdentElement();
			if (ident != null) ident.replaceWithText(newName);
		}
		else
		{
			throw new UnsupportedOperationException("rename for " + element + " not implemented yet");
		}
	}
}
