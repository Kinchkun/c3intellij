package org.c3lang.intellij.findUsages;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import org.c3lang.intellij.psi.C3ConstdefConstant;
import org.c3lang.intellij.psi.C3EnumAccessExpr;
import org.c3lang.intellij.psi.C3EnumConstant;
import org.c3lang.intellij.psi.reference.C3EnumAccessReference;
import org.jetbrains.annotations.NotNull;

/**
 * Makes Find Usages work for enum constants and constdef constants used in {@code Type.CONSTANT}
 * accesses.
 *
 * <p>Those usages are modelled by {@link C3EnumAccessReference}, which is attached via a
 * {@code psi.referenceContributor}. Contributed references are honoured by Go to Declaration
 * ({@code findReferenceAt}) but are invisible to {@link ReferencesSearch} unless their host
 * element is a {@code ContributedReferenceHost} — which the generated {@code C3EnumAccessExpr}
 * is not. This executor bridges that gap without a grammar mixin / parser regeneration: it scans
 * the word index for the constant's name and reports every {@code Type.CONSTANT} access that
 * resolves back to the target.</p>
 */
public final class C3EnumConstantReferencesSearch
	extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>
{
	public C3EnumConstantReferencesSearch()
	{
		super(true);
	}

	@Override
	public void processQuery(
		@NotNull ReferencesSearch.SearchParameters parameters,
		@NotNull Processor<? super PsiReference> consumer)
	{
		PsiElement target = parameters.getElementToSearch();
		if (!(target instanceof C3EnumConstant) && !(target instanceof C3ConstdefConstant)) return;

		String name = ((PsiNamedElement) target).getName();
		if (name == null || name.isEmpty()) return;

		SearchScope scope = parameters.getEffectiveSearchScope();
		PsiSearchHelper.getInstance(target.getProject()).processElementsWithWord(
			(element, offsetInElement) ->
			{
				C3EnumAccessExpr access = PsiTreeUtil.getParentOfType(element, C3EnumAccessExpr.class, false);
				if (access == null) return true;
				PsiReference reference = new C3EnumAccessReference(access);
				return !reference.isReferenceTo(target) || consumer.process(reference);
			},
			scope, name, UsageSearchContext.IN_CODE, true);
	}
}
