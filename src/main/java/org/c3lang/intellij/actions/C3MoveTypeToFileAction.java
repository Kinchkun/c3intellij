package org.c3lang.intellij.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.C3ParserDefinition;
import org.c3lang.intellij.C3SourceFileType;
import org.c3lang.intellij.psi.C3CallablePsiElement;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3ImportDecl;
import org.c3lang.intellij.psi.C3ModuleDefinition;
import org.c3lang.intellij.psi.C3ModuleSection;
import org.c3lang.intellij.psi.C3TopLevel;
import org.c3lang.intellij.psi.C3TypeName;
import org.c3lang.intellij.psi.ShortType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Refactoring: move a type declaration (struct, enum, union, bitstruct, typedef, interface,
 * fault, ...) together with all of its member functions/macros — those defined in the same file —
 * into a new file in the same module.
 *
 * <p>The new file keeps the source file's {@code module ...;} statement and a copy of its imports,
 * so the moved declarations keep resolving. The moved declarations (with their {@code <* *>} doc
 * comments) are removed from the original file.</p>
 *
 * <p>Member functions are matched by receiver type within the <em>same file</em> only; methods of
 * the type living in other files of the module are left untouched.</p>
 */
public final class C3MoveTypeToFileAction extends AnAction
{
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread()
	{
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent e)
	{
		e.getPresentation().setEnabledAndVisible(locateType(e) != null);
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent e)
	{
		TypeTarget target = locateType(e);
		if (target == null) return;
		Context context = buildContext(e.getProject(), target);
		if (context == null) return;
		if (context.sourceDirectory() == null)
		{
			Messages.showErrorDialog(context.project, "The current file is not in a directory.", "Move to File");
			return;
		}

		Project project = context.project;
		String defaultName = context.typeName + ".c3";
		String input = Messages.showInputDialog(
			project,
			"File name or path (relative to the current directory):",
			"Move '" + context.typeName + "' to File",
			Messages.getQuestionIcon(),
			defaultName,
			new FileNameValidator(context.sourceDirectory()));
		if (input == null) return;

		String trimmed = input.trim();
		String relativePath = trimmed.endsWith(".c3") ? trimmed : trimmed + ".c3";

		WriteCommandAction.writeCommandAction(project)
			.withName("Move '" + context.typeName + "' to File")
			.run(() -> performMove(context, relativePath));
	}

	private static void performMove(@NotNull Context context, @NotNull String relativePath)
	{
		// Capture the source text of every moved unit before touching the tree.
		String fileText = context.sourceFile.getText();
		List<int[]> ranges = new ArrayList<>();
		List<PsiElement[]> toDelete = new ArrayList<>();
		for (C3TopLevel unit : context.units)
		{
			PsiElement first = leadingDocStart(unit);
			PsiElement last = trailingWhitespace(unit);
			ranges.add(new int[] { first.getTextRange().getStartOffset(), unit.getTextRange().getEndOffset() });
			toDelete.add(new PsiElement[] { first, last });
		}

		String content = buildContent(context, fileText, ranges);

		PsiDirectory directory = resolveDirectory(context.sourceDirectory(), relativePath);
		String fileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);
		if (directory.findFile(fileName) != null)
		{
			Messages.showErrorDialog(context.project, "File '" + fileName + "' already exists.", "Move to File");
			return;
		}

		PsiFile newFile = PsiFileFactory.getInstance(context.project)
			.createFileFromText(fileName, C3SourceFileType.INSTANCE, content);
		PsiElement added = directory.add(newFile);

		// Remove the moved declarations from the source (last to first to keep earlier nodes valid).
		PsiElement section = context.section;
		for (int i = toDelete.size() - 1; i >= 0; i--)
		{
			PsiElement[] range = toDelete.get(i);
			section.deleteChildRange(range[0], range[1]);
		}

		if (added instanceof PsiFile created && created.getVirtualFile() != null)
		{
			FileEditorManager.getInstance(context.project).openFile(created.getVirtualFile(), true);
		}
	}

	private static @NotNull String buildContent(@NotNull Context context, @NotNull String fileText, @NotNull List<int[]> ranges)
	{
		StringBuilder content = new StringBuilder();
		if (!context.moduleStatement.isEmpty())
		{
			content.append(context.moduleStatement).append("\n\n");
		}
		if (!context.imports.isEmpty())
		{
			for (String imp : context.imports) content.append(imp).append('\n');
			content.append('\n');
		}
		for (int[] range : ranges)
		{
			content.append(fileText, range[0], range[1]).append("\n\n");
		}
		return content.toString().stripTrailing() + "\n";
	}

	/** The earliest element of {@code unit}'s leading doc-comment block, or {@code unit} itself. */
	private static @NotNull PsiElement leadingDocStart(@NotNull C3TopLevel unit)
	{
		PsiElement start = unit;
		for (PsiElement prev = unit.getPrevSibling();
		     prev instanceof PsiWhiteSpace || isDocComment(prev);
		     prev = prev.getPrevSibling())
		{
			if (isDocComment(prev)) start = prev;
		}
		return start;
	}

	/** Includes the whitespace immediately following {@code unit} so the deletion leaves no gap. */
	private static @NotNull PsiElement trailingWhitespace(@NotNull C3TopLevel unit)
	{
		PsiElement next = unit.getNextSibling();
		return next instanceof PsiWhiteSpace ? next : unit;
	}

	private static @NotNull PsiDirectory resolveDirectory(@NotNull PsiDirectory base, @NotNull String relativePath)
	{
		String[] parts = relativePath.split("/");
		PsiDirectory dir = base;
		for (int i = 0; i < parts.length - 1; i++)
		{
			if (parts[i].isEmpty() || parts[i].equals(".")) continue;
			PsiDirectory child = dir.findSubdirectory(parts[i]);
			dir = child != null ? child : dir.createSubdirectory(parts[i]);
		}
		return dir;
	}

	private static boolean isDocComment(@Nullable PsiElement element)
	{
		return element instanceof PsiComment
			&& element.getNode() != null
			&& element.getNode().getElementType() == C3ParserDefinition.DOC_COMMENT;
	}

	/** Cheap check used by {@link #update}: is the caret on a movable type declaration? */
	private static @Nullable TypeTarget locateType(@NotNull AnActionEvent e)
	{
		Editor editor = e.getData(CommonDataKeys.EDITOR);
		PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
		if (editor == null || !(file instanceof C3File)) return null;

		PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
		C3TopLevel typeTopLevel = PsiTreeUtil.getParentOfType(at, C3TopLevel.class);
		if (typeTopLevel == null) return null;

		PsiElement declaration = typeDeclarationOf(typeTopLevel);
		if (declaration == null) return null;

		C3TypeName typeName = PsiTreeUtil.findChildOfType(declaration, C3TypeName.class);
		String name = typeName != null ? typeName.getName() : null;
		if (name == null || name.isEmpty()) return null;

		return new TypeTarget(file, typeTopLevel, name);
	}

	/** Gathers everything needed to perform the move for the located type, or null. */
	private static @Nullable Context buildContext(@Nullable Project project, @NotNull TypeTarget target)
	{
		if (project == null) return null;
		PsiFile file = target.file;
		C3TopLevel typeTopLevel = target.typeTopLevel;
		String name = target.typeName;

		C3ModuleDefinition section = PsiTreeUtil.getParentOfType(typeTopLevel, C3ModuleDefinition.class);
		if (section == null) return null;

		String moduleStatement = section instanceof C3ModuleSection ms ? ms.getModule().getText().trim() : "";

		List<String> imports = new ArrayList<>();
		for (C3ImportDecl imp : section.getImportDeclarations())
		{
			imports.add(imp.getText().trim());
		}

		// Collect the type and its same-file member functions/macros in source order.
		List<C3TopLevel> units = new ArrayList<>();
		for (C3TopLevel tl : PsiTreeUtil.getChildrenOfTypeAsList(section, C3TopLevel.class))
		{
			if (tl == typeTopLevel || isMemberOf(tl, name)) units.add(tl);
		}

		return new Context(project, file, (PsiElement) section, name, moduleStatement, imports, units);
	}

	/** The type declaration wrapped by {@code topLevel}, or null if it is not a type declaration. */
	private static @Nullable PsiElement typeDeclarationOf(@NotNull C3TopLevel topLevel)
	{
		if (topLevel.getTypeDecl() != null) return topLevel.getTypeDecl();
		if (topLevel.getTypedefDecl() != null) return topLevel.getTypedefDecl();
		if (topLevel.getInterfaceDefinition() != null) return topLevel.getInterfaceDefinition();
		if (topLevel.getFaultdefDecl() != null) return topLevel.getFaultdefDecl();
		if (topLevel.getAttrdefDecl() != null) return topLevel.getAttrdefDecl();
		if (topLevel.getAliasTypeDecl() != null) return topLevel.getAliasTypeDecl();
		return null;
	}

	/** True if {@code topLevel} is a function/macro whose receiver type is {@code typeName}. */
	private static boolean isMemberOf(@NotNull C3TopLevel topLevel, @NotNull String typeName)
	{
		C3CallablePsiElement callable = null;
		if (topLevel.getFuncDefinition() != null)
		{
			callable = topLevel.getFuncDefinition().getFuncDef();
		}
		else if (topLevel.getMacroDefinition() instanceof C3CallablePsiElement macro)
		{
			callable = macro;
		}
		if (callable == null) return false;

		ShortType receiver = callable.getType();
		return receiver != null && typeName.equals(receiver.getValue());
	}

	private record TypeTarget(@NotNull PsiFile file, @NotNull C3TopLevel typeTopLevel, @NotNull String typeName)
	{
	}

	private record Context(
		@NotNull Project project,
		@NotNull PsiFile sourceFile,
		@NotNull PsiElement section,
		@NotNull String typeName,
		@NotNull String moduleStatement,
		@NotNull List<String> imports,
		@NotNull List<C3TopLevel> units)
	{
		PsiDirectory sourceDirectory()
		{
			return sourceFile.getContainingDirectory();
		}
	}

	/** Rejects empty names and names that collide with an existing file in the target directory. */
	private record FileNameValidator(@Nullable PsiDirectory directory) implements InputValidatorEx
	{
		@Override
		public @Nullable String getErrorText(@Nullable String inputString)
		{
			if (inputString == null || inputString.isBlank()) return "File name must not be empty";
			if (inputString.endsWith("/")) return "A file name is required";
			return null;
		}

		@Override
		public boolean checkInput(@Nullable String inputString)
		{
			return getErrorText(inputString) == null;
		}

		@Override
		public boolean canClose(@Nullable String inputString)
		{
			return checkInput(inputString);
		}
	}
}
