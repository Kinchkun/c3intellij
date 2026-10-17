package org.c3lang.intellij.targets;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.c3lang.intellij.C3ProjectManifest;
import org.c3lang.intellij.wizard.C3ProjectTemplates;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Adds a target to a C3 workspace: an executable in {@code apps/<name>/{src,test}} (registered as a
 * {@code project.json} target) or a library in {@code lib/<name>.c3l/{src,test}} (added to the
 * {@code "dependencies"}).
 */
final class C3AddTargetDialog extends DialogWrapper
{
	private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

	private final ComboBox<VirtualFile> projectBox;
	private final JBRadioButton executable = new JBRadioButton("Executable  (apps/<name>/{src,test})", true);
	private final JBRadioButton library = new JBRadioButton("Library  (lib/<name>.c3l/{src,test})");
	private final JBTextField name = new JBTextField();
	private @Nullable VirtualFile createdFile;

	C3AddTargetDialog(@NotNull Project project, @NotNull List<VirtualFile> projects, @Nullable VirtualFile selected)
	{
		super(project);
		projectBox = new ComboBox<>(projects.toArray(VirtualFile[]::new));
		projectBox.setRenderer(SimpleListCellRenderer.create((label, dir, index) ->
			label.setText(dir == null ? "" : dir.getName() + "  (" + dir.getPath() + ")")));
		if (selected != null) projectBox.setSelectedItem(selected);
		ButtonGroup kind = new ButtonGroup();
		kind.add(executable);
		kind.add(library);
		setTitle("Add C3 Target");
		init();
	}

	@Override
	protected @NotNull JComponent createCenterPanel()
	{
		FormBuilder form = FormBuilder.createFormBuilder();
		if (projectBox.getItemCount() > 1) form.addLabeledComponent("Project:", projectBox);
		return form
			.addLabeledComponent("Kind:", executable)
			.addComponentToRightColumn(library)
			.addLabeledComponent("Name:", name)
			.getPanel();
	}

	@Override
	public @Nullable JComponent getPreferredFocusedComponent()
	{
		return name;
	}

	@Override
	protected @Nullable ValidationInfo doValidate()
	{
		String value = name.getText().trim();
		if (!NAME.matcher(value).matches())
		{
			return new ValidationInfo("Use letters, digits, '_' or '-', starting with a letter", name);
		}
		VirtualFile dir = (VirtualFile) projectBox.getSelectedItem();
		if (dir == null) return new ValidationInfo("Select a C3 project", projectBox);
		if (executable.isSelected())
		{
			for (C3ProjectManifest.Target target : C3ProjectManifest.targets(dir.getPath()))
			{
				if (target.name().equals(value)) return new ValidationInfo("Target '" + value + "' already exists", name);
			}
			if (dir.findFileByRelativePath("apps/" + value) != null)
			{
				return new ValidationInfo("apps/" + value + " already exists", name);
			}
		}
		else if (dir.findFileByRelativePath("lib/" + value + ".c3l") != null)
		{
			return new ValidationInfo("lib/" + value + ".c3l already exists", name);
		}
		return null;
	}

	@Override
	protected void doOKAction()
	{
		VirtualFile dir = (VirtualFile) projectBox.getSelectedItem();
		if (dir == null) return;
		String value = name.getText().trim();

		// project.json is rewritten on disk; don't lose (or clobber) unsaved edits to it.
		FileDocumentManager.getInstance().saveAllDocuments();
		try
		{
			Path base = Path.of(dir.getPath());
			String created = executable.isSelected()
				? C3ProjectTemplates.createAppTarget(base, value)
				: C3ProjectTemplates.createWorkspaceLibrary(base, value);
			LocalFileSystem.getInstance().refreshNioFiles(List.of(base.resolve("project.json")), false, false, null);
			dir.refresh(false, true);
			createdFile = dir.findFileByRelativePath(created);
		}
		catch (IOException e)
		{
			Messages.showErrorDialog(getContentPanel(), e.getMessage(), "Cannot Add Target");
			return;
		}
		super.doOKAction();
	}

	/** The main (or library source) file created by the dialog, to open in the editor. */
	@Nullable VirtualFile getCreatedFile()
	{
		return createdFile;
	}
}
