package org.c3lang.intellij.refactoring;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameHandler;
import org.c3lang.intellij.C3InterfaceFileType;
import org.c3lang.intellij.C3SourceFileType;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3ImportPath;
import org.c3lang.intellij.psi.C3Module;
import org.c3lang.intellij.psi.C3ModulePath;
import org.c3lang.intellij.psi.C3Path;
import org.c3lang.intellij.psi.ModuleName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rename refactoring for C3 modules. Triggered from <em>Refactor &rarr; Rename</em> when the caret
 * is on a {@code module ...;} declaration.
 *
 * <p>The module declaration is not a renameable PSI named element (the parser is generated and the
 * module path has no name-identifier owner), so a dedicated {@link RenameHandler} takes over instead
 * of the standard rename pipeline.</p>
 *
 * <p>Renaming module {@code foo} rewrites, across the whole project:</p>
 * <ul>
 *   <li>every {@code module foo;} declaration (in all files/sections);</li>
 *   <li>every {@code import foo;} statement;</li>
 *   <li>every qualifier prefix {@code foo::...};</li>
 *   <li>every module alias {@code alias x = module foo;} (also a {@code module_path}).</li>
 * </ul>
 *
 * <p>The rename cascades into nested modules: renaming {@code foo} to {@code baz} also turns
 * {@code foo::bar} into {@code baz::bar} along with that sub-module's imports and prefixes.</p>
 *
 * <p>Finally, a declaration file named after the (exact) module's last path segment, e.g.
 * {@code foo.c3} for {@code module foo}, is renamed to match the new name &mdash; but only when no
 * other C3 file in the project shares that filename and the destination does not already exist.</p>
 */
public final class C3ModuleRenameHandler implements RenameHandler
{
	private static final Pattern MODULE_NAME = Pattern.compile("[A-Za-z_]\\w*(::[A-Za-z_]\\w*)*");

	@Override
	public boolean isAvailableOnDataContext(@NotNull DataContext dataContext)
	{
		Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
		PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
		return moduleAtCaret(editor, file) != null;
	}

	@Override
	public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext)
	{
		C3Module module = moduleAtCaret(editor, file);
		if (module != null) renameWithDialog(project, module);
	}

	@Override
	public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext)
	{
		for (PsiElement element : elements)
		{
			C3Module module = element instanceof C3Module m
				? m
				: PsiTreeUtil.getParentOfType(element, C3Module.class, false);
			if (module != null)
			{
				renameWithDialog(project, module);
				return;
			}
		}
	}

	private static @Nullable C3Module moduleAtCaret(@Nullable Editor editor, @Nullable PsiFile file)
	{
		if (editor == null || !(file instanceof C3File)) return null;
		PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
		if (at == null) return null;
		return PsiTreeUtil.getParentOfType(at, C3Module.class, false);
	}

	private static void renameWithDialog(@NotNull Project project, @NotNull C3Module module)
	{
		String oldName = module.getModulePath().getText();

		String input = Messages.showInputDialog(
			project,
			"New module name:",
			"Rename Module '" + oldName + "'",
			Messages.getQuestionIcon(),
			oldName,
			new ModuleNameValidator());
		if (input == null) return;

		String newName = input.trim();
		if (newName.isEmpty() || newName.equals(oldName)) return;

		WriteCommandAction.writeCommandAction(project)
			.withName("Rename Module '" + oldName + "'")
			.run(() -> performRename(project, oldName, newName));
	}

	private static void performRename(@NotNull Project project, @NotNull String oldNameStr, @NotNull String newNameStr)
	{
		ModuleName oldName = new ModuleName(oldNameStr);

		GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
		Set<VirtualFile> files = new LinkedHashSet<>(FileTypeIndex.getFiles(C3SourceFileType.INSTANCE, scope));
		files.addAll(FileTypeIndex.getFiles(C3InterfaceFileType.INSTANCE, scope));

		PsiManager psiManager = PsiManager.getInstance(project);
		PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

		// Files declaring the exact (renamed) module — the only candidates for a file rename.
		Set<PsiFile> exactDeclarationFiles = new LinkedHashSet<>();

		for (VirtualFile virtualFile : files)
		{
			PsiFile psiFile = psiManager.findFile(virtualFile);
			if (!(psiFile instanceof C3File)) continue;

			List<Edit> edits = new ArrayList<>();

			// `module foo;` declarations and `alias x = module foo;` aliases.
			for (C3ModulePath modulePath : PsiTreeUtil.findChildrenOfType(psiFile, C3ModulePath.class))
			{
				String text = modulePath.getText();
				String replacement = rewrite(oldName, newNameStr, text);
				if (replacement == null) continue;

				TextRange range = modulePath.getTextRange();
				edits.add(new Edit(range.getStartOffset(), range.getEndOffset(), replacement));

				if (modulePath.getParent() instanceof C3Module && text.equals(oldNameStr))
				{
					exactDeclarationFiles.add(psiFile);
				}
			}

			// `import foo;` statements.
			for (C3ImportPath importPath : PsiTreeUtil.findChildrenOfType(psiFile, C3ImportPath.class))
			{
				String replacement = rewrite(oldName, newNameStr, importPath.getText());
				if (replacement == null) continue;

				TextRange range = importPath.getTextRange();
				edits.add(new Edit(range.getStartOffset(), range.getEndOffset(), replacement));
			}

			// `foo::...` qualifier prefixes (the trailing `::` is preserved).
			for (C3Path path : PsiTreeUtil.findChildrenOfType(psiFile, C3Path.class))
			{
				String text = path.getText();
				String name = text.endsWith("::") ? text.substring(0, text.length() - 2) : text;
				String replacement = rewrite(oldName, newNameStr, name);
				if (replacement == null) continue;

				int start = path.getTextRange().getStartOffset();
				edits.add(new Edit(start, start + name.length(), replacement));
			}

			applyEdits(documentManager, psiFile, edits);
		}

		renameDeclarationFiles(files, exactDeclarationFiles, oldName, new ModuleName(newNameStr));
	}

	/**
	 * Maps a module-name occurrence to its renamed form, or {@code null} if the rename does not
	 * affect it. A name is affected when it equals the old name or is nested under it: renaming
	 * {@code foo} rewrites {@code foo} &rarr; {@code newName} and {@code foo::bar} &rarr;
	 * {@code newName::bar}.
	 */
	private static @Nullable String rewrite(@NotNull ModuleName oldName, @NotNull String newName, @NotNull String occurrence)
	{
		ModuleName target = new ModuleName(occurrence);
		if (!oldName.covers(target)) return null;

		String relative = oldName.relativePathTo(target);
		if (relative == null) return null;
		return relative.isEmpty() ? newName : newName + "::" + relative;
	}

	private static void applyEdits(@NotNull PsiDocumentManager documentManager, @NotNull PsiFile psiFile, @NotNull List<Edit> edits)
	{
		if (edits.isEmpty()) return;

		Document document = documentManager.getDocument(psiFile);
		if (document == null) return;

		// Apply from the end of the file backwards so earlier offsets stay valid.
		edits.sort((a, b) -> Integer.compare(b.start(), a.start()));
		for (Edit edit : edits)
		{
			document.replaceString(edit.start(), edit.end(), edit.replacement());
		}
		documentManager.commitDocument(document);
	}

	private static void renameDeclarationFiles(
		@NotNull Collection<VirtualFile> allFiles,
		@NotNull Collection<PsiFile> exactDeclarationFiles,
		@NotNull ModuleName oldName,
		@NotNull ModuleName newName)
	{
		String oldSuffix = oldName.getSuffix();
		String newSuffix = newName.getSuffix();
		if (oldSuffix.equals(newSuffix)) return; // The filename would not change.

		for (PsiFile psiFile : exactDeclarationFiles)
		{
			VirtualFile virtualFile = psiFile.getVirtualFile();
			if (virtualFile == null) continue;

			// Only rename a file that is actually named after the module.
			if (!virtualFile.getNameWithoutExtension().equals(oldSuffix)) continue;

			// Don't guess when several files share the name — leave them all alone.
			String fileName = virtualFile.getName();
			long sameName = allFiles.stream().filter(file -> file.getName().equals(fileName)).count();
			if (sameName != 1) continue;

			String extension = virtualFile.getExtension();
			String newFileName = extension == null ? newSuffix : newSuffix + "." + extension;

			VirtualFile parent = virtualFile.getParent();
			if (parent != null && parent.findChild(newFileName) != null) continue; // Destination exists.

			psiFile.setName(newFileName);
		}
	}

	private record Edit(int start, int end, String replacement)
	{
	}

	/** Accepts a valid C3 module path: identifiers separated by {@code ::}. */
	private record ModuleNameValidator() implements InputValidatorEx
	{
		@Override
		public @Nullable String getErrorText(@Nullable String inputString)
		{
			if (inputString == null || inputString.trim().isEmpty()) return "Module name must not be empty";
			if (!MODULE_NAME.matcher(inputString.trim()).matches()) return "Not a valid module name";
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
