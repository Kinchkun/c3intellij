package org.c3lang.intellij.navigation;

import com.intellij.codeInsight.TargetElementEvaluatorEx2;
import com.intellij.psi.PsiElement;
import org.c3lang.intellij.psi.C3AccessIdent;
import org.c3lang.intellij.psi.C3BaseType;
import org.c3lang.intellij.psi.C3Path;
import org.c3lang.intellij.psi.C3PathConst;
import org.c3lang.intellij.psi.C3PathIdent;
import org.jetbrains.annotations.NotNull;

/**
 * Tells the platform which named elements count as a declaration under the caret.
 *
 * <p>The usage-site elements of this plugin (a type usage, an identifier, a member access, a
 * module path, a constant) are {@code PsiNamedElement}s too, for rename. Without this evaluator
 * the platform takes them for the declaration at the caret: Go to Implementation on
 * {@code frontend::Window} then searches implementations of the usage instead of navigating to
 * the struct and reports "No implementations found", and Find Usages falls back to the usage
 * itself whenever its reference is ambiguous.</p>
 */
public final class C3TargetElementEvaluator extends TargetElementEvaluatorEx2
{
	@Override
	public boolean isAcceptableNamedParent(@NotNull PsiElement parent)
	{
		return !(parent instanceof C3BaseType
			|| parent instanceof C3PathIdent
			|| parent instanceof C3AccessIdent
			|| parent instanceof C3Path
			|| parent instanceof C3PathConst);
	}
}
