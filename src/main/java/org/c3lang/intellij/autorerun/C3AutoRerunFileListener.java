package org.c3lang.intellij.autorerun;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Watches the VFS and, after a C3 source file ({@code .c3}/{@code .c3t}/{@code .c3i}) or a
 * {@code project.json} changes, asks each open project's {@link C3AutoRerunService} to rerun — but
 * only when auto-rerun is enabled there and the changed file is in that project's content.
 *
 * <p>Registered declaratively via {@code <applicationListeners>} in plugin.xml.</p>
 */
public final class C3AutoRerunFileListener implements BulkFileListener
{
	@Override
	public void after(@NotNull List<? extends @NotNull VFileEvent> events)
	{
		List<VirtualFile> changed = null;
		for (VFileEvent event : events)
		{
			if (!isRelevant(event.getPath())) continue;
			VirtualFile file = event.getFile();
			if (file == null) continue;
			if (changed == null) changed = new ArrayList<>();
			changed.add(file);
		}
		if (changed == null) return;

		for (Project project : ProjectManager.getInstance().getOpenProjects())
		{
			if (project.isDisposed()) continue;

			// Don't force-create the service for projects that never enabled the feature.
			C3AutoRerunService service = project.getServiceIfCreated(C3AutoRerunService.class);
			if (service == null || !service.isEnabled()) continue;

			if (belongsToProject(project, changed)) service.scheduleRerun();
		}
	}

	static boolean isRelevant(@NotNull String path)
	{
		return path.endsWith(".c3")
			|| path.endsWith(".c3t")
			|| path.endsWith(".c3i")
			|| path.endsWith("/project.json")
			|| path.equals("project.json");
	}

	static boolean belongsToProject(@NotNull Project project, @NotNull List<VirtualFile> files)
	{
		ProjectFileIndex index = ProjectFileIndex.getInstance(project);
		for (VirtualFile file : files)
		{
			if (file.isValid() && index.isInContent(file)) return true;
		}
		return false;
	}
}
