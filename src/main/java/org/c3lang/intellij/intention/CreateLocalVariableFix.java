package org.c3lang.intellij.intention;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3Expr;
import org.c3lang.intellij.psi.C3ExprStmt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Captures a discarded expression in a new local variable: turns {@code expr;} into
 * {@code Type name = expr;}. When the expression's type is known it pre-fills the type and a
 * snake_case name and lets the user rename via a live template; when it is not, the template starts
 * on an empty type field so the user can type both the type and a name.
 */
public final class CreateLocalVariableFix implements LocalQuickFix
{
	private final @Nullable String typeText;

	public CreateLocalVariableFix(@Nullable String typeText)
	{
		this.typeText = typeText;
	}

	@Override
	public @NotNull String getFamilyName()
	{
		return "Create local variable";
	}

	@Override
	public boolean startInWriteAction()
	{
		return true;
	}

	@Override
	public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
	{
		C3ExprStmt statement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), C3ExprStmt.class, false);
		if (statement == null) return;
		C3Expr expr = statement.getExpr();
		if (expr == null) return;

		String exprText = expr.getText();
		int start = statement.getTextRange().getStartOffset();
		int end = statement.getTextRange().getEndOffset();

		PsiFile file = statement.getContainingFile();
		Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
		boolean editorMatches = editor != null
			&& PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) == file;

		if (editorMatches)
		{
			editor.getDocument().deleteString(start, end);
			PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
			editor.getCaretModel().moveToOffset(start);

			Template template = TemplateManager.getInstance(project).createTemplate("", "");
			template.setToReformat(true);
			if (typeText != null)
			{
				template.addTextSegment(typeText + " ");
				template.addVariable("name", new ConstantNode(defaultName(typeText)), true);
			}
			else
			{
				template.addVariable("type", new ConstantNode("Type"), true);
				template.addTextSegment(" ");
				template.addVariable("name", new ConstantNode("value"), true);
			}
			template.addTextSegment(" = " + exprText + ";");
			TemplateManager.getInstance(project).startTemplate(editor, template);
		}
		else
		{
			// Headless / batch run: no editor to host a template, so insert plain placeholders.
			String type = typeText != null ? typeText : "Type";
			String name = typeText != null ? defaultName(typeText) : "value";
			Document document = PsiDocumentManager.getInstance(project).getDocument(file);
			if (document == null) return;
			document.replaceString(start, end, type + " " + name + " = " + exprText + ";");
			PsiDocumentManager.getInstance(project).commitDocument(document);
		}
	}

	/** A snake_case variable name derived from a (PascalCase) type, or {@code value} as a fallback. */
	private static @NotNull String defaultName(@NotNull String type)
	{
		String core = type.trim();
		int colon = core.lastIndexOf(':');
		if (colon >= 0) core = core.substring(colon + 1);
		int dot = core.lastIndexOf('.');
		if (dot >= 0) core = core.substring(dot + 1);

		StringBuilder identifier = new StringBuilder();
		for (int i = 0; i < core.length(); i++)
		{
			char c = core.charAt(i);
			if (Character.isLetterOrDigit(c) || c == '_') identifier.append(c);
			else if (identifier.length() > 0) break;
		}
		core = identifier.toString();

		// Lowercase (primitive) types like `int` would collide with a keyword; use a neutral name.
		if (core.isEmpty() || !Character.isUpperCase(core.charAt(0))) return "value";
		return core
			.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
			.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
			.toLowerCase(Locale.ROOT);
	}
}
