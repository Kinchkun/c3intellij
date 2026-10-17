package org.c3lang.intellij.testing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the console output of the C3 test runner ({@code std::core::runtime::run_tests}) line by
 * line into test events:
 * <pre>
 * Testing argparse_test::can_parse_arguments .. [PASS] (37.12us)
 * Testing argparse_test::can_parse_rest ....... [FAIL]
 *
 * ========== TEST LOG ============        (captured output of the failing test, optional)
 * ...
 * ========== TEST END ============        (not followed by a newline)
 * Test failed ^^^ ( argparse_test.c3:31 ) `[]` != `[file1, file2]`
 * </pre>
 * A leak is reported as {@code [FAIL] (12us) LEAKS DETECTED!} followed by the leak report. A
 * failing test's output runs until the next {@code Testing} line or the {@code N tests run.} summary.
 * Lines outside any test (compiler output, the summary) are reported as unhandled.
 */
public final class C3TestOutputParser
{
	/** Receives the parsed events; test names are {@code module::name}. */
	public interface Listener
	{
		void testStarted(@NotNull String name);

		/** {@code text} belongs to the running test (its log, backtrace or leak report), with its newline. */
		void testOutput(@NotNull String name, @NotNull String text);

		void testFailed(@NotNull String name, @NotNull String message, @NotNull String details);

		/** {@code durationMillis} is -1 when unknown. */
		void testFinished(@NotNull String name, long durationMillis);

		/** A line that isn't part of a test. */
		void unhandled(@NotNull String line);
	}

	private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
	private static final Pattern TESTING = Pattern.compile("^Testing (\\S+) \\.+ ?(.*)$");
	private static final Pattern PASS = Pattern.compile("\\[PASS] \\(([^)]*)\\)");
	private static final Pattern FAIL = Pattern.compile("\\[FAIL](?: \\(([^)]*)\\))?(.*)");
	private static final Pattern FAILED_AT = Pattern.compile("^Test failed \\^\\^\\^ \\( (.*?) \\) ?(.*)$");
	private static final Pattern SUMMARY = Pattern.compile("^(\\d+ tests? run\\.|Test Result: .*)$");
	private static final Pattern DURATION = Pattern.compile("([0-9.]+)\\s*(ns|us|µs|ms|s|min)");
	private static final String LOG_END = "========== TEST END ============";

	private final Listener listener;

	private @Nullable String current;
	private boolean failing;
	private long duration = -1;
	private @Nullable String message;
	private final StringBuilder details = new StringBuilder();

	public C3TestOutputParser(@NotNull Listener listener)
	{
		this.listener = listener;
	}

	/** Feeds one line of output (with or without its trailing newline). */
	public void line(@NotNull String raw)
	{
		String text = ANSI.matcher(raw).replaceAll("");
		String line = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
		if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);

		// The captured log ends without a newline, so whatever c3 prints next shares its line.
		int logEnd = line.indexOf(LOG_END);
		if (current != null && logEnd >= 0 && logEnd + LOG_END.length() < line.length())
		{
			line(line.substring(0, logEnd + LOG_END.length()));
			line(line.substring(logEnd + LOG_END.length()));
			return;
		}

		Matcher testing = TESTING.matcher(line);
		if (testing.matches())
		{
			finishCurrent();
			start(testing.group(1));
			result(testing.group(2));
			return;
		}

		if (current == null)
		{
			listener.unhandled(line);
			return;
		}

		if (SUMMARY.matcher(line).matches())
		{
			finishCurrent();
			listener.unhandled(line);
			return;
		}

		if (!failing)
		{
			// Uncaptured output (--test-nocapture) is printed before the result.
			if (result(line)) return;
			listener.testOutput(current, line + "\n");
			return;
		}

		Matcher failedAt = FAILED_AT.matcher(line);
		if (failedAt.matches())
		{
			String reason = failedAt.group(2).trim();
			message = reason.isEmpty() ? "Test failed at " + failedAt.group(1) : reason;
		}
		details.append(line).append('\n');
		listener.testOutput(current, line + "\n");
	}

	/** Ends a test cut short by the end of the output (e.g. a crash that killed the runner). */
	public void finish()
	{
		if (current != null && !failing)
		{
			failing = true;
			message = "Test did not finish";
		}
		finishCurrent();
	}

	private void start(@NotNull String name)
	{
		current = name;
		failing = false;
		duration = -1;
		message = null;
		details.setLength(0);
		listener.testStarted(name);
	}

	/** Handles a {@code [PASS]}/{@code [FAIL]} marker in {@code text}; returns whether there was one. */
	private boolean result(@NotNull String text)
	{
		String name = current;
		if (name == null) return false;

		Matcher pass = PASS.matcher(text);
		if (pass.find())
		{
			String before = text.substring(0, pass.start());
			if (!before.isBlank()) listener.testOutput(name, before + "\n");
			listener.testFinished(name, parseDuration(pass.group(1)));
			current = null;
			return true;
		}

		Matcher fail = FAIL.matcher(text);
		if (fail.find())
		{
			String before = text.substring(0, fail.start());
			if (!before.isBlank()) listener.testOutput(name, before + "\n");
			failing = true;
			if (fail.group(1) != null) duration = parseDuration(fail.group(1));
			if (fail.group(2).contains("LEAKS DETECTED")) message = "Memory leaks detected";
			return true;
		}

		if (!text.isBlank()) listener.testOutput(name, text + "\n");
		return false;
	}

	private void finishCurrent()
	{
		String name = current;
		if (name == null) return;
		if (failing)
		{
			String detailText = details.toString().strip();
			listener.testFailed(name, message != null ? message : "Test failed", detailText);
		}
		listener.testFinished(name, duration);
		current = null;
	}

	/** {@code 37.12us} → 0, {@code 1.5s} → 1500; -1 when not parsable. */
	static long parseDuration(@Nullable String text)
	{
		if (text == null) return -1;
		Matcher matcher = DURATION.matcher(text);
		if (!matcher.find()) return -1;
		double value = Double.parseDouble(matcher.group(1));
		double millis = switch (matcher.group(2))
		{
			case "ns" -> value / 1_000_000;
			case "us", "µs" -> value / 1_000;
			case "ms" -> value;
			case "s" -> value * 1_000;
			default -> value * 60_000;
		};
		return Math.round(millis);
	}

	/** The module of a test name: {@code argparse_test} for {@code argparse_test::can_parse_rest}. */
	public static @NotNull String moduleOf(@NotNull String name)
	{
		int separator = name.lastIndexOf("::");
		return separator > 0 ? name.substring(0, separator) : "";
	}

	/** The function of a test name: {@code can_parse_rest} for {@code argparse_test::can_parse_rest}. */
	public static @NotNull String functionOf(@NotNull String name)
	{
		int separator = name.lastIndexOf("::");
		return separator >= 0 ? name.substring(separator + 2) : name;
	}
}
