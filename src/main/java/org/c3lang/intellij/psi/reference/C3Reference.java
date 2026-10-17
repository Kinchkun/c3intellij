package org.c3lang.intellij.psi.reference;

import com.intellij.psi.PsiPolyVariantReference;
import org.c3lang.intellij.psi.C3PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A C3-typed reference: the link from a usage of a name to its declaration.
 *
 * <p>A {@code PsiReference} answers the question "what does this identifier point to?". It powers
 * Ctrl-click / go-to-declaration, find-usages, and rename. C3 uses
 * {@link PsiPolyVariantReference polyvariant} references because a single name may legitimately
 * resolve to several declarations (e.g. overloaded functions across imported modules) – hence
 * {@link #multiResolve()} returns a collection rather than a single element.
 *
 * <p>This interface just narrows the platform types to {@link C3PsiElement} for convenience. The
 * machinery lives in {@link C3ReferenceBase}; concrete references (function name, type, enum access,
 * struct member...) extend that and implement {@code multiResolve()}.
 */
public interface C3Reference extends PsiPolyVariantReference
{
	/** The single best declaration this reference points to, or {@code null} if unresolved. */
	@Override
	@Nullable C3PsiElement resolve();

	/** The usage-site element that owns this reference (the thing the user clicked). */
	@Override
	@NotNull C3PsiElement getElement();

	/** All declarations this name could refer to. The heart of each concrete reference. */
	Collection<C3PsiElement> multiResolve();
}
