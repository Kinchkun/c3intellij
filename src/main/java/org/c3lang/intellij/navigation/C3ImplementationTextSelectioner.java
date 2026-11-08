package org.c3lang.intellij.navigation;

import com.intellij.codeInsight.hint.ImplementationTextSelectioner;
import com.intellij.psi.PsiElement;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3FuncDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * The text Quick Definition (Ctrl+Shift+I) shows for a C3 declaration.
 *
 * <p>A call resolves to the function's header ({@code func_def}: the stub-backed, indexed part),
 * whose text ends before the body. The body is a sibling under the enclosing
 * {@code func_definition}, so the popup is widened to that, showing the whole function.</p>
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
		return element;
	}
}
