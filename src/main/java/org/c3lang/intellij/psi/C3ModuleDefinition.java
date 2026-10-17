package org.c3lang.intellij.psi;

import com.intellij.openapi.project.DumbService;
import org.c3lang.intellij.index.NameIndexService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Objects;

public interface C3ModuleDefinition extends C3ModuleNamePsiElement
{
	@NotNull List<ModuleName> getImports();
	@NotNull List<C3ImportDecl> getImportDeclarations();
	@NotNull List<C3ImportPath> getImportPaths();

	boolean containsImportOrSameModule(@NotNull C3FullyQualifiedNamePsiElement callable);
	boolean contains(@NotNull C3PathIdent pathIdent);
	boolean contains(@NotNull C3Path path);
	@NotNull List<C3ImportPath> getImportOf(@NotNull C3PathIdent pathIdent);
	@NotNull List<C3ImportPath> getImportOf(@NotNull C3PathIdentExpr pathIdentExpr);
	@NotNull List<FullyQualifiedName> resolve(@NotNull C3PathIdentExpr pathIdent);
	@NotNull List<FullyQualifiedName> resolve(@NotNull C3Type type);
	@NotNull List<C3ImportPath> getImportPaths(@NotNull ModuleName moduleName);

	default boolean isSameModule(@Nullable ModuleName moduleName)
	{
		return Objects.equals(getModuleName(), moduleName);
	}

	default @Nullable ModuleName getImportedModuleCovering(@Nullable ModuleName moduleName)
	{
		ModuleName best = null;
		for (ModuleName imported : getImports())
		{
			if (imported.covers(moduleName)
				&& (best == null || imported.getValue().length() > best.getValue().length()))
			{
				best = imported;
			}
		}
		return best;
	}

	default @Nullable ModuleName getVisibleModulePrefix(@Nullable ModuleName moduleName)
	{
		if (isSameModule(moduleName)) return moduleName;

		ModuleName autoImported = ModuleName.autoImportedPrefix(moduleName);
		if (autoImported != null) return autoImported;

		return getImportedModuleCovering(moduleName);
	}

	/**
	 * {@link #resolve(C3Type)} is text-only (it also runs while stubs are built), so it places an
	 * unqualified type in this module. When this module doesn't declare it, use the index to find the
	 * visible module (an import, or the auto-imported {@code std::core}) that does — e.g.
	 * {@code ParsedArgs} declared in an imported {@code mystd::args}. Returns {@code fqn} unchanged
	 * while indexing, when the type is declared here, or when the match is ambiguous.
	 */
	default @Nullable FullyQualifiedName resolveDeclaringModule(@Nullable FullyQualifiedName fqn)
	{
		if (fqn == null || !isSameModule(fqn.getModule())) return fqn;
		if (DumbService.isDumb(getProject())) return fqn;

		FullyQualifiedName visible = null;
		for (C3FullyQualifiedNamePsiElement element :
			NameIndexService.INSTANCE.findByNameEndsWith("::" + fqn.getName(), getProject()))
		{
			if (!(element instanceof C3TypeName)) continue;
			FullyQualifiedName declared = element.getFqName();
			if (!declared.getName().equals(fqn.getName())) continue;
			if (isSameModule(declared.getModule())) return fqn;
			if (getVisibleModulePrefix(declared.getModule()) == null) continue;
			if (visible != null && !visible.equals(declared)) return fqn;
			visible = declared;
		}
		return visible != null ? visible : fqn;
	}
}
