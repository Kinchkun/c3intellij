package org.c3lang.intellij.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.index.NameIndexService;
import org.c3lang.intellij.psi.C3CallablePsiElement;
import org.c3lang.intellij.psi.C3FullyQualifiedNamePsiElement;
import org.c3lang.intellij.psi.C3ModuleDefinition;
import org.c3lang.intellij.psi.C3Path;
import org.c3lang.intellij.psi.C3PathAtIdent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves go-to-declaration for {@code @}-prefixed macro references such as {@code @pool()} to their
 * {@code macro @pool(...)} declarations.
 *
 * <p>In the grammar these are {@code path_at_ident} nodes with no name-identifier owner and no mixin,
 * so they carry no {@link com.intellij.psi.PsiReference}. A contributed reference does not help either:
 * for our own-language elements {@code getReferences()} (which navigation walks) ignores
 * {@code PsiReferenceContributor} references unless the host implements {@code ContributedReferenceHost}
 * — and that marker, in turn, suppresses the mixin {@code getReference()} the rest of the plugin relies
 * on. A dedicated {@link GotoDeclarationHandler} sidesteps all of that.</p>
 *
 * <p>Macro names are indexed including the leading {@code @}, so the lookup keeps it.</p>
 */
public final class C3AtMacroGotoDeclarationHandler implements GotoDeclarationHandler
{
	@Override
	public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor)
	{
		if (sourceElement == null) return null;

		C3PathAtIdent atIdent = PsiTreeUtil.getParentOfType(sourceElement, C3PathAtIdent.class, false);
		if (atIdent == null) return null;

		String name = atIdentName(atIdent);
		if (name.isEmpty()) return null;

		C3ModuleDefinition moduleDefinition = atIdent.getModuleDefinition();
		List<PsiElement> targets = new ArrayList<>();
		for (C3FullyQualifiedNamePsiElement el :
			NameIndexService.INSTANCE.findByNameEndsWith(name, atIdent.getProject()))
		{
			if (el instanceof C3CallablePsiElement
				&& name.equals(el.getFqName().getName())
				&& moduleDefinition.containsImportOrSameModule(el))
			{
				targets.add(el);
			}
		}
		return targets.isEmpty() ? null : targets.toArray(PsiElement.EMPTY_ARRAY);
	}

	/** The {@code @name} part of the reference, without the optional module-path prefix. */
	private static @NotNull String atIdentName(@NotNull C3PathAtIdent atIdent)
	{
		C3Path path = atIdent.getPath();
		String text = atIdent.getText();
		return path != null ? text.substring(path.getTextLength()) : text;
	}
}
