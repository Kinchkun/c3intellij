package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3ModuleDefinition;
import org.c3lang.intellij.psi.ModuleName;
import org.jetbrains.annotations.NotNull;

/**
 * Adds an {@code import <module>;} statement so an unqualified, unresolved name (e.g. a type used as
 * {@code Object* data}) resolves.
 *
 * <p>Unlike {@link AddImportQuickFix} — which is meant for a qualified usage and shortens the
 * {@code module::Name} prefix it imports — this fix only inserts the import, leaving the already
 * unqualified usage untouched.</p>
 */
public final class C3ImportTypeQuickFix implements LocalQuickFix
{
	private final String moduleName;

	public C3ImportTypeQuickFix(@NotNull ModuleName moduleName)
	{
		this.moduleName = moduleName.getValue();
	}

	@Override
	public @NotNull String getFamilyName()
	{
		return "Import " + moduleName;
	}

	@Override
	public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
	{
		PsiElement element = descriptor.getPsiElement();
		if (element == null) return;

		C3ModuleDefinition moduleSection = PsiTreeUtil.getParentOfType(element, C3ModuleDefinition.class);
		if (moduleSection == null) return;

		AddImportQuickFix.ImportAction action = AddImportQuickFix.addImportAsText(new ModuleName(moduleName), moduleSection);
		if (action == null || action instanceof AddImportQuickFix.ImportAction.Imported) return;

		PsiFile file = element.getContainingFile();
		Document document = PsiDocumentManager.getInstance(project).getDocument(file);
		if (document == null) return;

		action.write(document);
		PsiDocumentManager.getInstance(project).commitDocument(document);
	}
}
