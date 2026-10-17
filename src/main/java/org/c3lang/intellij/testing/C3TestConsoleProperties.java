package org.c3lang.intellij.testing;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import org.jetbrains.annotations.NotNull;

/** Console properties of the C3 test tree: parses c3 test output and locates tests in the sources. */
public final class C3TestConsoleProperties extends SMTRunnerConsoleProperties implements SMCustomMessagesParsing
{
	public static final String FRAMEWORK = "C3";

	public C3TestConsoleProperties(@NotNull RunConfiguration configuration, @NotNull Executor executor)
	{
		super(configuration, FRAMEWORK, executor);
		setIdBasedTestTree(false);
	}

	@Override
	public OutputToGeneralTestEventsConverter createTestEventsConverter(@NotNull String testFrameworkName,
	                                                                    @NotNull TestConsoleProperties consoleProperties)
	{
		return new C3TestEventsConverter(testFrameworkName, consoleProperties);
	}

	@Override
	public SMTestLocator getTestLocator()
	{
		return C3TestLocator.INSTANCE;
	}
}
