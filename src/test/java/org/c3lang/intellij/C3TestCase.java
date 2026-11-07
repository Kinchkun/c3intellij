package org.c3lang.intellij;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Base class for C3 platform tests.
 *
 * <p>The tests run inside the local CLion, whose bundled plugins keep working in the background
 * during the run and occasionally get blamed on whichever test happens to be running:</p>
 * <ul>
 *   <li>A startup activity enables the javadoc auto-popup at some point. The platform's
 *   settings-damage check in {@code tearDown} then fails the current test, so the default is
 *   restored before that check.</li>
 *   <li>The Rd framework (the protocol to CLion's Radler backend) logs an error when one of its
 *   callbacks runs after the light project was disposed between tests. The test logger reports
 *   every logged error as a failure of the current test, so errors logged by Rd are kept as plain
 *   log entries instead.</li>
 * </ul>
 */
public abstract class C3TestCase extends BasePlatformTestCase
{
	/** Common prefix of the Rd framework's packages ({@code com.jetbrains.rd}, {@code com.jetbrains.rdclient}, ...). */
	private static final String RD_PACKAGE_PREFIX = "com.jetbrains.rd";

	private static final LoggedErrorProcessor IGNORE_RD_ERRORS = new LoggedErrorProcessor()
	{
		@Override
		public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message,
			String @NotNull [] details, @Nullable Throwable t)
		{
			return isLoggedByRd() ? EnumSet.of(Action.LOG) : super.processError(category, message, details, t);
		}
	};

	@Override
	protected void runBare(@NotNull ThrowableRunnable<Throwable> runnable) throws Throwable
	{
		LoggedErrorProcessor.executeWith(IGNORE_RD_ERRORS, () -> super.runBare(runnable));
	}

	@Override
	protected void tearDown() throws Exception
	{
		try
		{
			CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO = new CodeInsightSettings().AUTO_POPUP_JAVADOC_INFO;
		}
		finally
		{
			super.tearDown();
		}
	}

	/**
	 * Whether the error currently being logged on this thread was logged by the Rd framework: the
	 * first frame below the logging machinery belongs to an Rd package. Only that logging site
	 * counts; the frames below it are often a test's own, because tests pump the event queue Rd
	 * dispatches on.
	 */
	private static boolean isLoggedByRd()
	{
		for (StackTraceElement frame : new Throwable().getStackTrace())
		{
			String cls = frame.getClassName();
			if (cls.startsWith("com.intellij.openapi.diagnostic.")
				|| cls.startsWith("com.intellij.testFramework.")
				|| cls.startsWith(C3TestCase.class.getName()))
			{
				continue;
			}
			return cls.startsWith(RD_PACKAGE_PREFIX);
		}
		return false;
	}
}
