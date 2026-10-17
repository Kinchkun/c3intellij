package org.c3lang.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Re-evaluates the libraries of {@link C3ProjectLibrariesRootsProvider} when a {@code project.json}
 * of the workspace changes its dependencies, so adding e.g. {@code "mystd"} indexes that library
 * without reopening the project.
 *
 * <p>Registered declaratively via {@code <applicationListeners>} in plugin.xml.</p>
 */
public final class C3ProjectManifestListener implements BulkFileListener
{
	@Override
	public void after(@NotNull List<? extends @NotNull VFileEvent> events)
	{
		for (VFileEvent event : events)
		{
			if (!event.getPath().endsWith("/project.json")) continue;

			for (Project project : ProjectManager.getInstance().getOpenProjects())
			{
				String basePath = project.getBasePath();
				if (project.isDisposed() || basePath == null) continue;
				// Any C3 project of the workspace, not only one at the IDE project root.
				if (!Path.of(event.getPath()).startsWith(Path.of(basePath))) continue;

				reloadLibraries(project, false);
			}
		}
	}

	/**
	 * Re-reads the dependency libraries of all {@code project.json}s and, when they changed (or
	 * always with {@code force}), makes the IDE re-index the library roots.
	 */
	public static void reloadLibraries(@NotNull Project project, boolean force)
	{
		List<Path> libraries = C3ProjectLibrariesRootsProvider.dependencyPaths(project);
		List<Path> previous = project.getUserData(C3ProjectLibrariesRootsProvider.DEPENDENCIES_KEY);
		if (!force && Objects.equals(libraries, previous)) return;

		project.putUserData(C3ProjectLibrariesRootsProvider.DEPENDENCIES_KEY, libraries);
		ApplicationManager.getApplication().invokeLater(
			() -> ApplicationManager.getApplication().runWriteAction(
				() -> ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(
					EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN)),
			project.getDisposed());
	}
}
