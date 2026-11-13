package org.c3lang.intellij.testing;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Whether test runs show what the tests print. The C3 test runner captures the output of a test and
 * prints it only when the test fails; {@code --test-show-output} turns the capturing off. The choice
 * is made with the toggle in the test toolbar and kept per project, in the workspace file (it is a
 * personal preference, not a project setting).
 */
public final class C3TestOutputSetting
{
	static final String SHOW_OUTPUT = "--test-show-output";
	/** The older spelling of the same option. */
	static final String NO_CAPTURE = "--test-nocapture";

	private static final String KEY = "c3.test.showOutput";

	private C3TestOutputSetting() {}

	public static boolean isShown(@NotNull Project project)
	{
		return PropertiesComponent.getInstance(project).getBoolean(KEY, false);
	}

	public static void setShown(@NotNull Project project, boolean shown)
	{
		PropertiesComponent.getInstance(project).setValue(KEY, shown, false);
	}

	/** Whether these {@code c3c test} arguments leave the output of the tests uncaptured. */
	public static boolean showsOutput(@NotNull List<String> arguments)
	{
		return arguments.contains(SHOW_OUTPUT) || arguments.contains(NO_CAPTURE);
	}

	/** Appends {@code --test-show-output} when the toggle is on and the arguments do not ask for it already. */
	public static void apply(@NotNull Project project, @NotNull List<String> arguments)
	{
		if (isShown(project) && !showsOutput(arguments)) arguments.add(SHOW_OUTPUT);
	}
}
