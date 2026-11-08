package org.c3lang.intellij.psi.reference;

import org.c3lang.intellij.psi.C3FullyQualifiedNamePsiElement;
import org.c3lang.intellij.psi.C3ModuleDefinition;
import org.c3lang.intellij.psi.C3PsiElement;
import org.c3lang.intellij.psi.ModuleName;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Picks the declarations closest to a usage when a name is declared in several visible modules:
 * the usage's own module wins over explicitly imported modules, which win over the auto-imported
 * {@code std::core}. A usage with more than one target navigates nowhere in the IDE, so
 * {@code Color} in {@code ted::frontend} must not be ambiguous with {@code sdl::Color}.
 */
public final class C3Candidates
{
	private C3Candidates()
	{
	}

	public static @NotNull List<C3PsiElement> closest(
		@NotNull Collection<? extends C3FullyQualifiedNamePsiElement> candidates, @NotNull C3ModuleDefinition context)
	{
		List<C3PsiElement> best = new ArrayList<>();
		int bestRank = Integer.MAX_VALUE;
		for (C3FullyQualifiedNamePsiElement candidate : candidates)
		{
			int rank = rank(candidate.getModuleName(), context);
			if (rank < bestRank)
			{
				best.clear();
				bestRank = rank;
			}
			if (rank == bestRank) best.add(candidate);
		}
		return best;
	}

	private static int rank(ModuleName module, @NotNull C3ModuleDefinition context)
	{
		if (context.isSameModule(module)) return 0;
		if (context.getImportedModuleCovering(module) != null) return 1;
		return 2;
	}
}
