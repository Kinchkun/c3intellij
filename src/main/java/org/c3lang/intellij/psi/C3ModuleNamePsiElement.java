package org.c3lang.intellij.psi;

import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;

public interface C3ModuleNamePsiElement extends C3PsiElement
{
	@Nullable ModuleName getModuleName();

	default boolean isSameModule(@NotNull C3FullyQualifiedNamePsiElement other)
	{
		return Objects.equals(getModuleName(), other.getModuleName());
	}

	default boolean isImported(@NotNull C3FullyQualifiedNamePsiElement other)
	{
		// strict = false so that when `this` is itself the C3ModuleDefinition (e.g. the
		// import context of a reference) it is used directly, instead of looking for a
		// non-existent enclosing module and throwing.
		C3ModuleDefinition moduleDefinition =
			PsiTreeUtil.getParentOfType(this, C3ModuleDefinition.class, false);
		if (moduleDefinition == null) return false;
		return other.getModuleDefinition().equals(moduleDefinition)
			|| moduleDefinition.getVisibleModulePrefix(other.getModuleName()) != null;
	}

	default @NotNull String textToInsert(@Nullable ModuleName imported, @NotNull C3FullyQualifiedNamePsiElement element)
	{
		if (isSameModule(element) || element.getModuleName() == null)
			return element.getFqName().getName();

		if (imported != null)
		{
			String relativePath = imported.relativePathTo(element.getModuleName());
			if (relativePath != null)
			{
				if (relativePath.isEmpty()) return element.getFqName().getSuffixName();
				return relativePath + "::" + element.getFqName().getName();
			}
		}

		return element.getFqName().getFullName();
	}
}
