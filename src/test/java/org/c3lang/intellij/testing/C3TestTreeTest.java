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
}
