package org.c3lang.intellij;

import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.c3lang.intellij.index.NameIndex;
import org.c3lang.intellij.psi.C3InterfaceImpl;
import org.c3lang.intellij.psi.C3PsiElement;
import org.c3lang.intellij.psi.C3TypeName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Feeds C3 symbols (types, functions, macros, constants, faults, ...) to Search Everywhere /
 * Go to Symbol, using the name stub index. Symbols are searched by their simple name.
 */
public final class C3SymbolContributor implements ChooseByNameContributorEx
{
	@Override
	public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter)
	{
		StubIndex.getInstance().processAllKeys(
			NameIndex.KEY,
			key -> processor.process(simpleName(key)),
			scope,
			filter);
	}

	@Override
	public void processElementsWithName(
		@NotNull String name, @NotNull Processor<? super NavigationItem> processor, @NotNull FindSymbolParameters parameters)
	{
		Project project = parameters.getProject();
		GlobalSearchScope scope = parameters.getSearchScope();
		IdFilter filter = parameters.getIdFilter();
		StubIndex index = StubIndex.getInstance();

		// Collect the matching full-name keys first; processElements must not be nested inside
		// processAllKeys (the platform forbids it to avoid stub-index deadlocks).
		List<String> keys = new ArrayList<>();
		index.processAllKeys(NameIndex.KEY, key -> {
			if (name.equals(simpleName(key))) keys.add(key);
			return true;
		}, scope, filter);

		for (String key : keys)
		{
			index.processElements(NameIndex.KEY, key, project, scope, filter, C3PsiElement.class, element -> {
				// Skip the interface name in `struct X (Interface)` — that is a usage, not a
				// declaration, and would otherwise duplicate the interface in the symbol list.
				if (element instanceof C3TypeName && element.getParent() instanceof C3InterfaceImpl) return true;
				if (element instanceof NavigationItem item) return processor.process(item);
				return true;
			});
		}
	}

	private static @NotNull String simpleName(@NotNull String fqName)
	{
		int idx = fqName.lastIndexOf("::");
		return idx >= 0 ? fqName.substring(idx + 2) : fqName;
	}
}
