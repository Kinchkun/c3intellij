package org.c3lang.intellij.debugger;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import org.c3lang.intellij.C3BuildRunConfiguration;
import org.c3lang.intellij.C3CommandRunConfiguration;
import org.c3lang.intellij.C3CompileRunConfiguration;
import org.c3lang.intellij.C3ConfigurationType;
import org.c3lang.intellij.C3TestCase;

import java.util.List;

/**
 * The debug build of a project keeps its object files: on macOS the debug info lives in them, and without them
 * (and without a .dSYM, which the built-in linker does not write) no breakpoint is ever hit.
 */
public class C3DebugBuildArgumentsTest extends C3TestCase
{
	private RunConfiguration configuration(ConfigurationFactory factory)
	{
		return RunManager.getInstance(getProject()).createConfiguration("debug", factory).getConfiguration();
	}

	public void testTestBuildKeepsObjectFiles()
	{
		C3CommandRunConfiguration test = (C3CommandRunConfiguration) configuration(C3ConfigurationType.getInstance().testFactory());
		test.setArgs("http_client --test-filter c3_workspace::can_make_a_get_call -q");
		List<String> arguments = C3DebugRunner.buildArguments(test);
		assertNotNull(arguments);
		assertEquals("test", arguments.get(0));
		assertTrue(arguments.toString(), arguments.containsAll(List.of(
			"http_client", "--test-filter", "c3_workspace::can_make_a_get_call", "--suppress-run", "--keep-obj")));
		assertFalse("a quiet build hides the executable path: " + arguments, arguments.contains("-q"));
	}

	public void testProjectBuildKeepsObjectFiles()
	{
		C3BuildRunConfiguration build = (C3BuildRunConfiguration) configuration(C3ConfigurationType.getInstance().runProjectFactory());
		build.setTarget("ted");
		build.setArgs("--keep-obj");
		assertEquals(List.of("build", "ted", "--keep-obj"), C3DebugRunner.buildArguments(build));
	}

	/** A single file is linked with the system linker, which writes a .dSYM. */
	public void testSingleFileBuildLeavesNoObjectFiles()
	{
		C3CompileRunConfiguration compile = (C3CompileRunConfiguration) configuration(C3ConfigurationType.getInstance().singleFileFactory());
		compile.setSourceFile("/tmp/scratch.c3");
		assertEquals(List.of("compile", "/tmp/scratch.c3", "-O0"), C3DebugRunner.buildArguments(compile));
	}

	/** c3c runs `build/testrun --test-filter <name>`; without the filter the binary runs every test of the target. */
	public void testTestBinaryGetsTheFilter()
	{
		assertEquals(
			List.of("--test-filter", "c3_workspace::can_make_a_get_call", "--test-show-output"),
			C3DebugRunner.testBinaryArguments("http_client --test-filter c3_workspace::can_make_a_get_call -q"));
		assertEquals(
			List.of("--test-nosort", "--test-nocapture"),
			C3DebugRunner.testBinaryArguments("http_client --test-nosort -O1 --test-nocapture"));
		assertEquals(List.of("--test-show-output"), C3DebugRunner.testBinaryArguments(null));
	}
}
