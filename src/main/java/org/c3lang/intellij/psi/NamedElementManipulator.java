package org.c3lang.intellij.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Teaches the platform how to physically change the text of a named C3 element.
 *
 * <p>When a refactoring (rename, import insertion, etc.) wants to replace the text of an element, it
 * asks the registered {@link AbstractElementManipulator}. For C3 named elements we simply forward to
 * {@code setName()} – the mixin impls already know how to rewrite the identifier token in the AST.
 *
 * <p>Registered in {@code plugin.xml} via {@code <lang.elementManipulator
 * forClass="...C3PsiNamedElement"/>}.
 */
public class NamedElementManipulator extends AbstractElementManipulator<PsiNamedElement>
{
	@Override
	public @Nullable PsiNamedElement handleContentChange(
		@NotNull PsiNamedElement element,
		@NotNull TextRange range,
		@Nullable String newContent)
	{
		if (newContent != null)
		{
			// Delegate the actual identifier rewrite to the element's own setName().
			element.setName(newContent);
			return element;
		}
		return null;
	}
}
