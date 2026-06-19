package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.index.NameIndexService;
import org.c3lang.intellij.psi.C3BaseType;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3FullyQualifiedNamePsiElement;
import org.c3lang.intellij.psi.C3ModuleDefinition;
import org.c3lang.intellij.psi.C3TypeName;
import org.c3lang.intellij.psi.C3Visitor;
import org.c3lang.intellij.psi.ModuleName;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Flags an unqualified type name that names a type declared in another, un-imported module — e.g.
 * {@code Object* data} where {@code Object} lives in a module the current file does not import — and
 * offers a quick fix to import each module that declares such a type.
 *
 * <p>Scope and guards:</p>
 * <ul>
 *   <li>primitive/keyword types ({@code int}, {@code void}, …) are ignored;</li>
 *   <li>qualified usages ({@code mod::Type}) are handled by {@link ImportModuleInspection};</li>
 *   <li>a type that is already visible (same module, imported, or auto-imported {@code std::core})
 *       is not flagged;</li>
 *   <li>a name with no matching type declaration anywhere is left alone — it may be a generic/type
 *       parameter or simply unknown, and there is no import to offer.</li>
 * </ul>
 */
public final class C3UnresolvedTypeInspection extends LocalInspectionTool
{
	@Override
	public @NotNull String getDisplayName()
	{
		return "Unresolved type";
	}

	@Override
	public @NotNull String getGroupDisplayName()
	{
		return "C3";
	}

	@Override
	public @NotNull PsiElementVisitor buildVisitor(
		@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session)
	{
		if (!(session.getFile() instanceof C3File)) return PsiElementVisitor.EMPTY_VISITOR;

		Project project = holder.getProject();
		return new C3Visitor()
		{
			@Override
			public void visitBaseType(@NotNull C3BaseType baseType)
			{
				if (baseType.isPrimitiveType()) return;
				if (baseType.getPath() != null) return; // Qualified — handled by ImportModuleInspection.

				LeafPsiElement nameElement = baseType.getNameIdentElement();
				String name = baseType.getNameIdent();
				if (nameElement == null || name == null || name.isEmpty()) return;

				C3ModuleDefinition moduleSection = PsiTreeUtil.getParentOfType(baseType, C3ModuleDefinition.class);
				if (moduleSection == null) return;

				Set<ModuleName> importable = new LinkedHashSet<>();
				for (C3FullyQualifiedNamePsiElement el : NameIndexService.INSTANCE.findType(baseType, project))
				{
					if (!(el instanceof C3TypeName)) continue;
					ModuleName module = el.getModuleName();
					if (module == null) continue;

					// Already reachable (same module / imported / auto-imported std::core): not an error.
					if (moduleSection.getVisibleModulePrefix(module) != null) return;
					importable.add(module);
				}

				if (importable.isEmpty()) return; // No such type anywhere — nothing to import.

				List<LocalQuickFix> fixes = new ArrayList<>();
				for (ModuleName module : importable) fixes.add(new C3ImportTypeQuickFix(module));

				holder.registerProblem(
					nameElement,
					"Unresolved type '" + name + "' — not imported",
					ProblemHighlightType.GENERIC_ERROR,
					fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
			}
		};
	}
}
