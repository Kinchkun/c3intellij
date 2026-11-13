package org.c3lang.intellij.testing;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import org.c3lang.intellij.C3CommandRunConfiguration;
import org.c3lang.intellij.C3ConfigurationType;
import org.c3lang.intellij.C3TestCase;

import java.util.List;

/** The real SM test runner builds a module suite with a green and a red test from c3 output. */
public class C3TestTreeTest extends C3TestCase
{
	private static final String OUTPUT = """
		c3c compile-test --libdir lib --lib argparse lib/argparse.c3l/test/argparse_test.c3 --build-dir build -o build/argparse_test --test-filter argparse_test::
		--------------------- TESTS ----------------------
		Testing argparse_test::can_parse_arguments .. [PASS] (37.12us)
		Testing argparse_test::can_parse_rest ....... [FAIL]

		Test failed ^^^ ( argparse_test.c3:31 ) `[]` != `[file1, file2]`

		2 tests run.

		Test Result: FAILED: 1 passed, 1 failed, 0 skipped.
		Program linked to executable 'out/build/argparse_test'.
		Launching ./out/build/argparse_test --test-filter argparse_test::
		Program completed with exit code 1.
		""";

	public void testBuildsTreeFromOutput()
	{
		C3CommandRunConfiguration configuration = (C3CommandRunConfiguration) C3ConfigurationType.getInstance()
			.libraryTestFactory().createTemplateConfiguration(getProject());
		C3TestConsoleProperties properties =
			new C3TestConsoleProperties(configuration, DefaultRunExecutor.getRunExecutorInstance());

		SMTestProxy.SMRootTestProxy root = new SMTestProxy.SMRootTestProxy();
		GeneralToSMTRunnerEventsConvertor processor =
			new GeneralToSMTRunnerEventsConvertor(getProject(), root, C3TestConsoleProperties.FRAMEWORK);
		C3TestEventsConverter converter =
			(C3TestEventsConverter) properties.createTestEventsConverter(C3TestConsoleProperties.FRAMEWORK, properties);
		converter.setProcessor(processor);
		try
		{
			converter.startTesting();
			for (String line : OUTPUT.split("(?<=\n)")) converter.process(line, ProcessOutputTypes.STDOUT);
			converter.flushBufferOnProcessTermination(1);
			converter.finishTesting();
			UIUtil.dispatchAllInvocationEvents();

			List<? extends SMTestProxy> suites = root.getChildren();
			assertEquals(1, suites.size());
			SMTestProxy suite = suites.getFirst();
			assertEquals("argparse_test", suite.getName());
			assertEquals("c3suite://argparse_test", suite.getLocationUrl());

			List<? extends SMTestProxy> tests = suite.getChildren();
			assertEquals(2, tests.size());
			assertEquals("can_parse_arguments", tests.get(0).getName());
			assertTrue(tests.get(0).isPassed());
			assertEquals("c3test://argparse_test::can_parse_arguments", tests.get(0).getLocationUrl());

			SMTestProxy failed = tests.get(1);
			assertEquals("can_parse_rest", failed.getName());
			assertTrue(failed.isDefect());
			assertEquals("`[]` != `[file1, file2]`", failed.getErrorMessage());
			assertTrue(failed.getStacktrace().contains("argparse_test.c3:31"));
		}
		finally
		{
			Disposer.dispose(converter);
			Disposer.dispose(processor);
		}
	}

	/** With `--test-show-output` a test's prints come before its result; they belong to that test's node. */
	private static final String CHATTY_OUTPUT = """
		------------------- TESTS --------------------
		Testing c3_workspace::can_make_a_get_call .. ????
		stdout line
		[PASS] (811.21us)
		Testing c3_workspace::quiet ................ [PASS] (1.00us)

		2 tests run.

		Test Result: PASSED: 2 passed, 0 failed, 0 skipped.
		""";

	public void testOutputOfAPassingTestBelongsToItsNode()
	{
		C3CommandRunConfiguration configuration = (C3CommandRunConfiguration) C3ConfigurationType.getInstance()
			.testFactory().createTemplateConfiguration(getProject());
		C3TestConsoleProperties properties =
			new C3TestConsoleProperties(configuration, DefaultRunExecutor.getRunExecutorInstance());

		SMTestProxy.SMRootTestProxy root = new SMTestProxy.SMRootTestProxy();
		GeneralToSMTRunnerEventsConvertor processor =
			new GeneralToSMTRunnerEventsConvertor(getProject(), root, C3TestConsoleProperties.FRAMEWORK);
		C3TestEventsConverter converter =
			(C3TestEventsConverter) properties.createTestEventsConverter(C3TestConsoleProperties.FRAMEWORK, properties);
		converter.setProcessor(processor);
		try
		{
			converter.startTesting();
			for (String line : CHATTY_OUTPUT.split("(?<=\n)")) converter.process(line, ProcessOutputTypes.STDOUT);
			converter.flushBufferOnProcessTermination(0);
			converter.finishTesting();
			UIUtil.dispatchAllInvocationEvents();

			SMTestProxy suite = root.getChildren().getFirst();
			assertEquals(2, suite.getChildren().size());
			SMTestProxy chatty = suite.getChildren().get(0);
			SMTestProxy quiet = suite.getChildren().get(1);
			assertTrue(chatty.isPassed());
			assertEquals("????\nstdout line\n", outputOf(chatty));
			assertEquals("", outputOf(quiet));
		}
		finally
		{
			Disposer.dispose(converter);
			Disposer.dispose(processor);
		}
	}

	private static String outputOf(SMTestProxy test)
	{
		StringBuilder output = new StringBuilder();
		test.printOn(new com.intellij.execution.testframework.Printer()
		{
			@Override public void print(String text, com.intellij.execution.ui.ConsoleViewContentType contentType) { output.append(text); }
			@Override public void printHyperlink(String text, com.intellij.execution.filters.HyperlinkInfo info) { output.append(text); }
			@Override public void onNewAvailable(com.intellij.execution.testframework.Printable printable) { printable.printOn(this); }
			@Override public void mark() {}
		});
		return output.toString();
	}

	public void testToggleAddsTheShowOutputOption()
	{
		C3CommandRunConfiguration configuration = (C3CommandRunConfiguration) C3ConfigurationType.getInstance()
			.testFactory().createTemplateConfiguration(getProject());
		configuration.setArgs("http_client --test-filter c3_workspace::can_make_a_get_call");
		try
		{
			assertFalse(configuration.commandParameters().contains("--test-show-output"));

			C3TestOutputSetting.setShown(getProject(), true);
			assertEquals(
				List.of("http_client", "--test-filter", "c3_workspace::can_make_a_get_call", "--test-show-output"),
				configuration.commandParameters());

			// Asked for in the configuration itself: not added twice.
			configuration.setArgs("http_client --test-nocapture");
			assertEquals(List.of("http_client", "--test-nocapture"), configuration.commandParameters());
		}
		finally
		{
			C3TestOutputSetting.setShown(getProject(), false);
		}
	}

	/** Only test commands take the option. */
	public void testToggleLeavesOtherCommandsAlone()
	{
		C3CommandRunConfiguration docgen = (C3CommandRunConfiguration) C3ConfigurationType.getInstance()
			.docgenFactory().createTemplateConfiguration(getProject());
		C3TestOutputSetting.setShown(getProject(), true);
		try
		{
			assertFalse(docgen.commandParameters().contains("--test-show-output"));
		}
		finally
		{
			C3TestOutputSetting.setShown(getProject(), false);
		}
	}

	/** Written into the configuration, the option would keep the output visible whatever the toggle says. */
	public void testToggleTakesTheOptionOutOfTheConfiguration()
	{
		C3CommandRunConfiguration configuration = (C3CommandRunConfiguration) C3ConfigurationType.getInstance()
			.testFactory().createTemplateConfiguration(getProject());
		configuration.setArgs("http_client --test-filter c3_workspace::can_make_a_get_call --test-show-output --test-nocapture");
		C3ShowTestOutputAction.dropOutputOptions(configuration);
		assertEquals("http_client --test-filter c3_workspace::can_make_a_get_call", configuration.getArgs());
		assertFalse(configuration.commandParameters().contains("--test-show-output"));

		configuration.setArgs("http_client -O1");
		C3ShowTestOutputAction.dropOutputOptions(configuration);
		assertEquals("http_client -O1", configuration.getArgs());
	}
}
