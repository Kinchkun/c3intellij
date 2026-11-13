package org.c3lang.intellij.testing;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import org.c3lang.intellij.C3CommandRunConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Toggle next to Rerun and Stop in the toolbar of a C3 test run: show what the tests print
 * ({@code c3c test --test-show-output}) or let the runner capture it, so only a failing test shows
 * its output. With the toggle on, the output is attached to its test: select a test to read only
 * what that test printed. Switching it reruns the tests.
 *
 * <p>It is one of the run's own actions and not an additional action of the test console: the
 * platform puts those into the options submenu of the toolbar's "more" button, two clicks away.</p>
 */
public final class C3ShowTestOutputAction extends ToggleAction implements DumbAware
{
	private final ExecutionEnvironment environment;

	public C3ShowTestOutputAction(@NotNull ExecutionEnvironment environment)
	{
		super("Show Test Output",
			"Show what the tests print, also for passing tests (c3c test --test-show-output). Reruns the tests.",
			AllIcons.Debugger.Console);
		this.environment = environment;
	}

	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread()
	{
		return ActionUpdateThread.BGT;
	}

	@Override
	public boolean isSelected(@NotNull AnActionEvent event)
	{
		return C3TestOutputSetting.isShown(environment.getProject());
	}

	@Override
	public void setSelected(@NotNull AnActionEvent event, boolean state)
	{
		C3TestOutputSetting.setShown(environment.getProject(), state);
		if (environment.getRunProfile() instanceof C3CommandRunConfiguration configuration) dropOutputOptions(configuration);
		ExecutionUtil.restart(environment);
	}

	/**
	 * The toolbar this action sits in does not always paint a pressed toggle, so the state is also
	 * told by the icon (a green dot while output is shown) and by the text.
	 */
	@Override
	public void update(@NotNull AnActionEvent event)
	{
		super.update(event);
		boolean shown = isSelected(event);
		event.getPresentation().setIcon(shown ? ExecutionUtil.getLiveIndicator(AllIcons.Debugger.Console) : AllIcons.Debugger.Console);
		event.getPresentation().setText(shown ? "Hide Test Output" : "Show Test Output");
		event.getPresentation().setDescription(shown
			? "The tests' output is shown (c3c test --test-show-output). Click to capture it again; only failing tests then show their output. Reruns the tests."
			: "Show what the tests print, also for passing tests (c3c test --test-show-output). Reruns the tests.");
	}

	/**
	 * Removes {@code --test-show-output} and {@code --test-nocapture} from the arguments of the
	 * configuration: written there they would keep the output visible whatever the toggle says.
	 */
	static void dropOutputOptions(@NotNull C3CommandRunConfiguration configuration)
	{
		String args = configuration.getArgs();
		if (args == null || args.isBlank()) return;
		List<String> kept = new ArrayList<>();
		for (String argument : args.trim().split("\\s+"))
		{
			if (!argument.equals(C3TestOutputSetting.SHOW_OUTPUT) && !argument.equals(C3TestOutputSetting.NO_CAPTURE)) kept.add(argument);
		}
		String remaining = String.join(" ", kept);
		if (!remaining.equals(args)) configuration.setArgs(remaining);
	}
}
