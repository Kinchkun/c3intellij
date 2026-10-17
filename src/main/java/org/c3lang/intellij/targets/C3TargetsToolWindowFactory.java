package org.c3lang.intellij.targets;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.c3lang.intellij.C3Projects;
import org.jetbrains.annotations.NotNull;

/** The C3 Targets tool window, shown for IDE projects containing at least one {@code project.json}. */
public final class C3TargetsToolWindowFactory implements ToolWindowFactory, DumbAware
{
	@Override
	public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow)
	{
		C3TargetsPanel panel = new C3TargetsPanel(project);
		Content content = ContentFactory.getInstance().createContent(panel, null, false);
		content.setDisposer(panel);
		toolWindow.getContentManager().addContent(content);
	}

	@Override
	public boolean shouldBeAvailable(@NotNull Project project)
	{
		return !C3Projects.findAll(project).isEmpty();
	}
}
