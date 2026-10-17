package org.c3lang.intellij.testing;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class C3TestOutputParserTest
{
	/** Records events as compact strings. */
	private static List<String> parse(String output)
	{
		List<String> events = new ArrayList<>();
		C3TestOutputParser parser = new C3TestOutputParser(new C3TestOutputParser.Listener()
		{
			@Override public void testStarted(@NotNull String name) { events.add("start " + name); }
			@Override public void testOutput(@NotNull String name, @NotNull String text) { events.add("out " + name + ": " + text.strip()); }
			@Override public void testFailed(@NotNull String name, @NotNull String message, @NotNull String details) { events.add("fail " + name + ": " + message); }
			@Override public void testFinished(@NotNull String name, long durationMillis) { events.add("finish " + name + " " + durationMillis); }
			@Override public void unhandled(@NotNull String line) { if (!line.isBlank()) events.add("text " + line); }
		});
		for (String line : output.split("(?<=\n)")) parser.line(line);
		parser.finish();
		return events;
	}

	@Test
	public void passAndFailFromCompileTest()
	{
		List<String> events = parse("""
			c3c compile-test --libdir lib --lib argparse lib/argparse.c3l/test/argparse_test.c3
			--------------------- TESTS ----------------------
			Testing argparse_test::can_parse_arguments .. [PASS] (37.12us)
			Testing argparse_test::can_parse_rest ....... [FAIL]

			Test failed ^^^ ( argparse_test.c3:31 ) `[]` != `[file1, file2]`

			2 tests run.

			Test Result: FAILED: 1 passed, 1 failed, 0 skipped.
			Program completed with exit code 1.
			""");

		assertEquals(List.of(
			"text c3c compile-test --libdir lib --lib argparse lib/argparse.c3l/test/argparse_test.c3",
			"text --------------------- TESTS ----------------------",
			"start argparse_test::can_parse_arguments",
			"finish argparse_test::can_parse_arguments 0",
			"start argparse_test::can_parse_rest",
			"out argparse_test::can_parse_rest: ",
			"out argparse_test::can_parse_rest: Test failed ^^^ ( argparse_test.c3:31 ) `[]` != `[file1, file2]`",
			"out argparse_test::can_parse_rest: ",
			"fail argparse_test::can_parse_rest: `[]` != `[file1, file2]`",
			"finish argparse_test::can_parse_rest -1",
			"text 2 tests run.",
			"text Test Result: FAILED: 1 passed, 1 failed, 0 skipped.",
			"text Program completed with exit code 1."), events);
	}

	@Test
	public void capturedLogEndsWithoutNewline()
	{
		List<String> events = parse("""
			Testing a::first .. [FAIL]

			========== TEST LOG ============
			a::first

			hello from the test
			========== TEST END ============
			Test failed ^^^ ( a.c3:3 ) boom
			Testing a::second . [PASS] (1.5ms)
			""".replace("TEST END ============\n", "TEST END ============"));

		assertEquals("out a::first: hello from the test", events.get(5));
		assertEquals(List.of(
			"fail a::first: boom",
			"finish a::first -1",
			"start a::second",
			"finish a::second 2"), events.subList(events.size() - 4, events.size()));
	}

	@Test
	public void leaksAndAnsiColors()
	{
		List<String> events = parse("Testing m::leaky .. \u001B[0;31m[FAIL]\u001B[0m (3us) LEAKS DETECTED!\nleak report line\n1 test run.\n");
		assertEquals(List.of(
			"start m::leaky",
			"out m::leaky: leak report line",
			"fail m::leaky: Memory leaks detected",
			"finish m::leaky 0",
			"text 1 test run."), events);
	}

	@Test
	public void crashedRunnerFailsTheRunningTest()
	{
		List<String> events = parse("Testing m::crash .. ");
		assertEquals(List.of("start m::crash", "fail m::crash: Test did not finish", "finish m::crash -1"), events);
	}

	@Test
	public void namesAndDurations()
	{
		assertEquals("std::io::path_test", C3TestOutputParser.moduleOf("std::io::path_test::joins"));
		assertEquals("joins", C3TestOutputParser.functionOf("std::io::path_test::joins"));
		assertEquals(1500, C3TestOutputParser.parseDuration("1.5s"));
		assertEquals(-1, C3TestOutputParser.parseDuration("?"));
		assertEquals("##teamcity[testFailed name='t' message='a|'b|n|[c|]||']",
			C3TestEventsConverter.Message.testFailed("t").addAttribute("message", "a'b\n[c]|").toString());
	}
}
