package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Discards a statement's unused result explicitly: turns {@code expr;} into {@code (void)expr;},
 * which the C3 compiler and {@link C3UnusedExpressionResultInspection} accept as intentional.
 */
public final class DiscardResultFix implements LocalQuickFix
{
	@Override
	public @NotNull String getFamilyName()
	{
		return "Discard result with (void)";
	}

	@Override
	public boolean startInWriteAction()
	{
		return true;
	}

	@Override
	public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
	{
		PsiElement expr = descriptor.getPsiElement();
		if (expr == null) return;
		Document document = PsiDocumentManager.getInstance(project).getDocument(expr.getContainingFile());
		if (document == null) return;
		document.insertString(expr.getTextRange().getStartOffset(), "(void)");
		PsiDocumentManager.getInstance(project).commitDocument(document);
	}
}
