package org.c3lang.intellij.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.c3lang.intellij.C3ProjectSettingsState;
import org.c3lang.intellij.C3SettingsState;
import org.c3lang.intellij.C3StdlibRoots;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.Objects;

/**
 * Settings → Languages &amp; Frameworks → C3: the application-wide standard library path (with
 * detection) and this project's optional override.
 */
public final class C3Configurable implements Configurable
{
	private final Project project;
	private C3SettingsComponent c3SettingsComponent;

	public C3Configurable(@NotNull Project project)
	{
		this.project = project;
	}

	@Override
	public @Nullable JComponent createComponent()
	{
		c3SettingsComponent = new C3SettingsComponent(project);
		reset();
		return c3SettingsComponent.getMainPanel();
	}

	@Override
	public boolean isModified()
	{
		if (c3SettingsComponent == null) return false;
		return !Objects.equals(c3SettingsComponent.getStdlibPath(), nullToEmpty(C3SettingsState.getInstance().stdlibPath))
			|| !Objects.equals(c3SettingsComponent.getProjectStdlibPath(), nullToEmpty(C3ProjectSettingsState.getInstance(project).stdlibPath));
	}

	@Override
	public void apply()
	{
		if (c3SettingsComponent == null) return;
		C3SettingsState settings = C3SettingsState.getInstance();
		C3ProjectSettingsState projectSettings = C3ProjectSettingsState.getInstance(project);

		String stdlibPath = c3SettingsComponent.getStdlibPath();
		boolean applicationChanged = !stdlibPath.equals(nullToEmpty(settings.stdlibPath));
		if (applicationChanged)
		{
			if (stdlibPath.equals(c3SettingsComponent.getDetectedPath())) settings.setDetectedStdlib(stdlibPath);
			else settings.setManualStdlib(stdlibPath);
		}

		String projectPath = c3SettingsComponent.getProjectStdlibPath();
		boolean projectChanged = !projectPath.equals(nullToEmpty(projectSettings.stdlibPath));
		projectSettings.stdlibPath = projectPath;

		if (applicationChanged) C3StdlibRoots.refreshAll();
		else if (projectChanged) C3StdlibRoots.refresh(project);
	}

	@Override
	public void reset()
	{
		if (c3SettingsComponent == null) return;
		c3SettingsComponent.setStdlibPath(nullToEmpty(C3SettingsState.getInstance().stdlibPath));
		c3SettingsComponent.setProjectStdlibPath(nullToEmpty(C3ProjectSettingsState.getInstance(project).stdlibPath));
	}

	@Override
	public void disposeUIResources()
	{
		c3SettingsComponent = null;
	}

	@Override
	public @Nls String getDisplayName()
	{
		return "C3";
	}

	private static @NotNull String nullToEmpty(@Nullable String value)
	{
		return value == null ? "" : value;
	}
}
