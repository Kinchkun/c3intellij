package org.c3lang.intellij.navigation;

import com.intellij.codeInsight.hint.ImplementationTextSelectioner;
import com.intellij.psi.PsiElement;
import org.c3lang.intellij.psi.C3AliasName;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3FuncDefinition;
import org.c3lang.intellij.psi.C3GlobalName;
import org.c3lang.intellij.psi.C3TypeName;
import org.jetbrains.annotations.NotNull;

/**
 * The text Quick Definition (Ctrl+Shift+I) shows for a C3 declaration.
 *
 * <p>A usage resolves to the named, stub-backed part of a declaration, which is smaller than the
 * declaration: a call resolves to the function header ({@code func_def}), whose body is a sibling
 * under {@code func_definition}; a type resolves to its name ({@code type_name}) inside the struct,
 * enum, typedef or interface declaration; a global or alias resolves to its name element. The
 * popup is widened to the whole declaration.</p>
 */
public final class C3ImplementationTextSelectioner implements ImplementationTextSelectioner
{
	@Override
	public int getTextStartOffset(@NotNull PsiElement element)
	{
		return shown(element).getTextRange().getStartOffset();
	}

	@Override
	public int getTextEndOffset(@NotNull PsiElement element)
	{
		return shown(element).getTextRange().getEndOffset();
	}

	private static @NotNull PsiElement shown(@NotNull PsiElement element)
	{
		if (element instanceof C3FuncDef && element.getParent() instanceof C3FuncDefinition definition)
		{
			return definition;
		}
		// type_name, global_name and alias_name occur only directly under their declaration.
		if (element instanceof C3TypeName || element instanceof C3GlobalName || element instanceof C3AliasName)
		{
			PsiElement declaration = element.getParent();
			if (declaration != null) return declaration;
		}
		return element;
	}
}
