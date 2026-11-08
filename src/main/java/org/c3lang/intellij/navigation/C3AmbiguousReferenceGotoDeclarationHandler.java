package org.c3lang.intellij.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import org.c3lang.intellij.psi.C3File;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Offers a chooser when a reference has several targets. The platform's default navigation
 * navigates nowhere for a reference with more than one resolve result, so a name declared twice
 * in equally visible modules had no Go to Declaration at all.
 */
public final class C3AmbiguousReferenceGotoDeclarationHandler implements GotoDeclarationHandler
{
	@Override
	public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor)
	{
		if (sourceElement == null || !(sourceElement.getContainingFile() instanceof C3File file)) return null;
		PsiReference reference = file.findReferenceAt(offset);
		if (reference instanceof PsiMultiReference multi) reference = innermost(multi, offset);
		if (!(reference instanceof PsiPolyVariantReference poly)) return null;
		List<PsiElement> targets = new ArrayList<>();
		for (ResolveResult result : poly.multiResolve(false))
		{
			if (result.getElement() != null) targets.add(result.getElement());
		}
		return targets.size() > 1 ? targets.toArray(PsiElement.EMPTY_ARRAY) : null;
	}

	/** Of several references at the caret (a module path inside a type), the one with the smallest range. */
	private static @Nullable PsiReference innermost(PsiMultiReference multi, int offset)
	{
		PsiReference best = null;
		for (PsiReference reference : multi.getReferences())
		{
			int start = reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getStartOffset();
			int end = start + reference.getRangeInElement().getLength();
			if (offset < start || offset > end) continue;
			if (best == null || reference.getRangeInElement().getLength() < best.getRangeInElement().getLength()) best = reference;
		}
		return best;
	}
}
