package org.c3lang.intellij.autorerun;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * While auto-rerun is enabled, watches edits to C3 source files in this project and, shortly after
 * typing stops, asks the service to save and rerun (see {@link C3AutoRerunService#scheduleAutoSave}).
 * This makes the run happen live, without a manual save.
 *
 * <p>One instance is registered per project (scoped to that project's service lifetime); it filters
 * document events to files that belong to its own project.</p>
 */
public final class C3AutoSaveDocumentListener implements DocumentListener
{
	private final Project project;

	public C3AutoSaveDocumentListener(@NotNull Project project)
	{
		this.project = project;
	}

	@Override
	public void documentChanged(@NotNull DocumentEvent event)
	{
		if (project.isDisposed()) return;

		C3AutoRerunService service = project.getServiceIfCreated(C3AutoRerunService.class);
		if (service == null || !service.isEnabled()) return;

		VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
		if (file == null || !C3AutoRerunFileListener.isRelevant(file.getName())) return;
		if (!ProjectFileIndex.getInstance(project).isInContent(file)) return;

		service.scheduleAutoSave();
	}
}
