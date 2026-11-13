package org.c3lang.intellij.autorerun;

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;

/**
 * Toolbar toggle (next to Run) that turns auto-rerun on/off for the current project. Shown only when
 * a C3 run/test configuration is selected. When enabled, {@link C3AutoRerunFileListener} reruns that
 * configuration after the sources or {@code project.json} change.
 */
public final class C3ToggleAutoRerunAction extends ToggleAction implements DumbAware
{
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread()
	{
		return ActionUpdateThread.BGT;
	}

	@Override
	public boolean isSelected(@NotNull AnActionEvent e)
	{
		Project project = e.getProject();
		return project != null && C3AutoRerunService.getInstance(project).isEnabled();
	}

	@Override
	public void setSelected(@NotNull AnActionEvent e, boolean state)
	{
		Project project = e.getProject();
		if (project != null) C3AutoRerunService.getInstance(project).setEnabled(state);
	}

	@Override
	public void update(@NotNull AnActionEvent e)
	{
		super.update(e);
		Project project = e.getProject();
		e.getPresentation().setEnabledAndVisible(
			project != null && C3AutoRerunService.hasC3ConfigurationSelected(project));

		// The main toolbar does not paint a pressed toggle, so the state is also told by the icon
		// (a green dot while auto-rerun is on) and by the text.
		boolean enabled = isSelected(e);
		e.getPresentation().setIcon(enabled ? ExecutionUtil.getLiveIndicator(AllIcons.Actions.Rerun) : AllIcons.Actions.Rerun);
		e.getPresentation().setText(enabled ? "Auto-Rerun C3 on Change: On" : "Auto-Rerun C3 on Change: Off");
	}
}
