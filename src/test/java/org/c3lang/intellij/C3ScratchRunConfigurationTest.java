package org.c3lang.intellij;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.vfs.VirtualFile;

public class C3ScratchRunConfigurationTest extends C3TestCase
{
	private static final String MAIN = "fn void main()\n{\n}\n";

	@Override
	protected void tearDown() throws Exception
	{
		try
		{
			RunManager runManager = RunManager.getInstance(getProject());
			for (RunnerAndConfigurationSettings settings : runManager.getAllSettings())
			{
				runManager.removeConfiguration(settings);
			}
		}
		finally
		{
			super.tearDown();
		}
	}

	public void testRunningAgainReusesTheConfiguration()
	{
		VirtualFile file = myFixture.configureByText("scratch.c3", MAIN).getVirtualFile();

		RunnerAndConfigurationSettings first = C3LineMarkerProvider.scratchConfiguration(getProject(), file);
		RunnerAndConfigurationSettings second = C3LineMarkerProvider.scratchConfiguration(getProject(), file);

		assertSame(first, second);
		assertEquals(1, compileRunConfigurations());
		assertEquals(file.getPath(), ((C3CompileRunConfiguration) first.getConfiguration()).getSourceFile());
	}

	public void testKeepsChangesToTheConfiguration()
	{
		VirtualFile file = myFixture.configureByText("scratch.c3", MAIN).getVirtualFile();
		var config = (C3CompileRunConfiguration) C3LineMarkerProvider.scratchConfiguration(getProject(), file).getConfiguration();
		config.setArgs("--verbose");

		var again = (C3CompileRunConfiguration) C3LineMarkerProvider.scratchConfiguration(getProject(), file).getConfiguration();

		assertEquals("--verbose", again.getArgs());
	}

	public void testEachFileGetsItsOwnConfiguration()
	{
		VirtualFile first = myFixture.configureByText("first.c3", MAIN).getVirtualFile();
		VirtualFile second = myFixture.configureByText("second.c3", MAIN).getVirtualFile();

		C3LineMarkerProvider.scratchConfiguration(getProject(), first);
		C3LineMarkerProvider.scratchConfiguration(getProject(), second);

		assertEquals(2, compileRunConfigurations());
	}

	private long compileRunConfigurations()
	{
		return RunManager.getInstance(getProject()).getAllSettings().stream()
			.filter(settings -> settings.getConfiguration() instanceof C3CompileRunConfiguration)
			.count();
	}
}
