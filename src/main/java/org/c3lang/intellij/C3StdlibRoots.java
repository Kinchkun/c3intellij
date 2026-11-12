package org.c3lang.intellij;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/** Applies a changed standard library path to the open projects, and runs detection in the background. */
public final class C3StdlibRoots
{
	private C3StdlibRoots()
	{
	}

	/** Re-indexes the library roots of {@code project} and refreshes the "not set" banners. */
	public static void refresh(@NotNull Project project)
	{
		if (project.isDisposed()) return;
		ApplicationManager.getApplication().invokeLater(
			() -> ApplicationManager.getApplication().runWriteAction(
				() -> ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(
					EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN)),
			project.getDisposed());
		EditorNotifications.getInstance(project).updateAllNotifications();
	}

	/** {@link #refresh(Project)} for every open project, after a change of the application-level setting. */
	public static void refreshAll()
	{
		for (Project project : ProjectManager.getInstance().getOpenProjects()) refresh(project);
	}

	/**
	 * Detects the standard library with the configured compiler in a background task, stores it as
	 * detected and re-indexes. When nothing is found a notification says so.
	 */
	public static void detectInBackground(@Nullable Project project)
	{
		ProgressManager.getInstance().run(new Task.Backgroundable(project, "Detecting C3 Standard Library", true)
		{
			private @Nullable Path detected;

			@Override public void run(@NotNull ProgressIndicator indicator)
			{
				detected = C3StdlibLocator.detect(C3SettingsState.getInstance().sdk);
			}

			@Override public void onSuccess()
			{
				C3SettingsState settings = C3SettingsState.getInstance();
				if (detected == null)
				{
					showNotification(project, "No C3 standard library found. Check the C3 Compiler setting, or set the path by hand.", NotificationType.WARNING);
					return;
				}
				settings.setDetectedStdlib(detected.toString());
				refreshAll();
				showNotification(project, "C3 standard library: " + detected, NotificationType.INFORMATION);
			}
		});
	}

	private static void showNotification(@Nullable Project project, @NotNull String text, @NotNull NotificationType type)
	{
		NotificationGroupManager.getInstance().getNotificationGroup("C3")
			.createNotification(text, type)
			.notify(project);
	}
}
