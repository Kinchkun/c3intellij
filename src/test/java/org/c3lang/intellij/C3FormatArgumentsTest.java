package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import org.c3lang.intellij.intention.C3FormatArgumentsInspection;

import java.util.List;

/**
 * The placeholders of a format string must match the arguments after it: an error for
 * {@code @format} functions (the compiler checks those), a warning for format functions without
 * the attribute, like {@code io::eprintfn}.
 */
public class C3FormatArgumentsTest extends C3TestCase
{
	private static final String IO = "module std::io;\n"
		+ "fn sz? printfn(String format, args...) @format(0) @maydiscard { return 0; }\n"
		+ "fn sz? eprintfn(String format, args...) @maydiscard { return 0; }\n"
		+ "fn sz? fprintf(void* out, String format, args...) @format(1) { return 0; }\n"
		+ "macro void info(String fmt, ..., int category = 0) @format(0) {}\n"
		+ "struct Buffer { int length; }\n"
		+ "fn sz? Buffer.appendf(&self, String format, args...) @maydiscard @format(1) { return 0; }\n"
		+ "fn void write(String text, int count) {}\n";

	private List<String> problems(String body)
	{
		myFixture.addFileToProject("io.c3", IO);
		myFixture.enableInspections(new C3FormatArgumentsInspection());
		myFixture.configureByText("app.c3", "module app;\nimport std::io;\nfn void f(int code, String body, any[] values)\n{\n" + body + "}\n");
		return myFixture.doHighlighting(HighlightSeverity.WARNING).stream()
			.filter(info -> info.getDescription() != null && info.getDescription().contains("formatting string"))
			.map(info -> info.getSeverity().getName() + ": " + info.getDescription())
			.toList();
	}

	public void testTooManyArgumentsForAnUncheckedFunctionIsAWarning()
	{
		assertEquals(
			List.of("WARNING: Too many arguments were provided for the formatting string: 1 placeholder, 2 arguments"),
			problems("    io::eprintfn(\"Response\\n\\tstatus=%s\\n\\tbody=\", code, body);\n"));
	}

	public void testMismatchForAFormatFunctionIsAnError()
	{
		assertEquals(
			List.of(
				"ERROR: Too few arguments were provided for the formatting string: 2 placeholders, 1 argument",
				"ERROR: Too many arguments were provided for the formatting string: 0 placeholders, 1 argument",
				"ERROR: Too few arguments were provided for the formatting string: 1 placeholder, 0 arguments",
				"ERROR: Too many arguments were provided for the formatting string: 1 placeholder, 2 arguments"),
			problems("    io::printfn(\"one %s two %s\", code);\n"
				+ "    io::printfn(\"none\", code);\n"
				+ "    io::fprintf(null, \"%d\");\n"
				+ "    io::info(\"%s\", code, body);\n"));
	}

	/** The receiver of `buffer.appendf(...)` is parameter 0 of `@format(1)` and no argument. */
	public void testMethodCallCountsFromTheFormatParameter()
	{
		assertEquals(
			List.of("ERROR: Too few arguments were provided for the formatting string: 2 placeholders, 1 argument"),
			problems("    Buffer buffer;\n    buffer.appendf(\"x %s %s\", code);\n    buffer.appendf(\"x %s\", code);\n"));
	}

	public void testMatchingAndUncheckableCallsAreFine()
	{
		List<String> problems = problems(
			"    io::printfn(\"status=%s body=%s\", code, body);\n"
				+ "    io::printfn(\"100%% of %d\", code);\n"
				+ "    io::printfn(\"%*d and %-8.3f and %.*f\", 5, code, 1.0, 2, 1.0);\n"
				+ "    io::printfn(\"plain\");\n"
				+ "    io::printfn(\"%s %s\", ...values);\n"
				+ "    io::printfn(body, code);\n"
				+ "    io::info(\"%s\", code, category: 3);\n"
				+ "    io::eprintfn(\"%s\", code);\n"
				+ "    io::write(\"%s %s\", 1);\n");
		assertEquals(problems.toString(), 0, problems.size());
	}
}
