package org.c3lang.intellij.navigation;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.util.Processor;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3TypeName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Go to Implementation (and the Quick Definition popup) for interfaces: on an interface it finds
 * the types implementing it, on an interface method the methods implementing it. From a usage
 * ({@code Printable value}, {@code value.print()}) the platform resolves to the declaration first.
 */
public final class C3ImplementationSearch extends QueryExecutorBase<PsiElement, DefinitionsScopedSearch.SearchParameters>
{
	public C3ImplementationSearch()
	{
		super(true);
	}

	@Override
	public void processQuery(
		@NotNull DefinitionsScopedSearch.SearchParameters parameters,
		@NotNull Processor<? super PsiElement> consumer)
	{
		PsiElement element = parameters.getElement();
		List<? extends PsiElement> implementations;
		if (element instanceof C3TypeName typeName && C3Implementations.interfaceOf(typeName) != null)
		{
			implementations = C3Implementations.implementers(typeName);
		}
		else if (element instanceof C3FuncDef method && C3Implementations.declaringInterface(method) != null)
		{
			implementations = C3Implementations.implementingMethods(method);
		}
		else
		{
			return;
		}

		SearchScope scope = parameters.getScope();
		for (PsiElement implementation : implementations)
		{
			if (!PsiSearchScopeUtil.isInScope(scope, implementation)) continue;
			if (!consumer.process(implementation)) return;
		}
	}
}
