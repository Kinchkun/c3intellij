package org.c3lang.intellij.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3StructDeclaration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alt+Enter intention on a struct that implements an interface: offers the interface's
 * {@code @optional} methods (not yet implemented) in a multi-select popup and generates
 * {@code @dynamic} stubs for the chosen ones.
 */
public final class ImplementOptionalInterfaceMethodsIntention implements IntentionAction
{
	@Override
	public @NotNull String getText()
	{
		return "Implement optional interface methods";
	}

	@Override
	public @NotNull String getFamilyName()
	{
		return "Implement optional interface methods";
	}

	@Override
	public boolean startInWriteAction()
	{
		return false; // shows a popup; the chosen stubs are written in their own command
	}

	@Override
	public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file)
	{
		C3StructDeclaration struct = structAt(editor, file);
		if (struct == null || struct.getInterfaceImpl() == null) return false;
		return !C3Interfaces.implementableOptionalMethods(struct).isEmpty();
	}

	@Override
	public void invoke(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file)
	{
		C3StructDeclaration struct = structAt(editor, file);
		if (struct == null || editor == null) return;

		List<C3FuncDef> optional = C3Interfaces.implementableOptionalMethods(struct);
		if (optional.isEmpty()) return;

		Map<String, C3FuncDef> byLabel = new LinkedHashMap<>();
		for (C3FuncDef method : optional) byLabel.put(label(method), method);

		JBPopupFactory.getInstance()
			.createPopupChooserBuilder(new ArrayList<>(byLabel.keySet()))
			.setTitle("Implement Optional Methods")
			.setItemsChosenCallback(selected -> {
				if (selected.isEmpty()) return;
				List<C3FuncDef> chosen = new ArrayList<>();
				for (String label : selected)
				{
					C3FuncDef method = byLabel.get(label);
					if (method != null) chosen.add(method);
				}
				WriteCommandAction.runWriteCommandAction(project,
					() -> C3Interfaces.insertStubs(project, struct, chosen));
			})
			.createPopup()
			.showInBestPositionFor(editor);
	}

	private static @Nullable C3StructDeclaration structAt(@Nullable Editor editor, @Nullable PsiFile file)
	{
		if (!(file instanceof C3File) || editor == null) return null;
		PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
		return PsiTreeUtil.getParentOfType(element, C3StructDeclaration.class);
	}

	private static @NotNull String label(@NotNull C3FuncDef method)
	{
		String returnType = method.getFuncHeader().getOptionalType().getText();
		String params = method.getFnParameterList().getText();
		return returnType + " " + C3Interfaces.methodSimpleName(method) + params;
	}
}
