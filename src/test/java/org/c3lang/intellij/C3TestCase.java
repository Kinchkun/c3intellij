package org.c3lang.intellij;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Base class for C3 platform tests.
 *
 * <p>A bundled CLion startup activity enables the javadoc auto-popup in the background at some
 * point during the run. The platform's settings-damage check in {@code tearDown} then blames
 * whichever test happened to be running, so restore the default before that check.</p>
 */
public abstract class C3TestCase extends BasePlatformTestCase
{
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
}
