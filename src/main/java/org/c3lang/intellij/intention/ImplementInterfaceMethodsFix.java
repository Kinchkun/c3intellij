package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3StructDeclaration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Generates {@code @dynamic} stubs for the interface methods a struct hasn't implemented. */
public final class ImplementInterfaceMethodsFix implements LocalQuickFix
{
	@Override
	public @NotNull String getFamilyName()
	{
		return "Implement interface methods";
	}

	@Override
	public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
	{
		PsiElement element = descriptor.getPsiElement();
		C3StructDeclaration struct = element instanceof C3StructDeclaration s
			? s
			: PsiTreeUtil.getParentOfType(element, C3StructDeclaration.class);
		if (struct == null) return;

		C3Interfaces.insertStubs(project, struct, C3Interfaces.missingMethods(struct));
	}
}
