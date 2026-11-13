package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import org.c3lang.intellij.intention.C3UnresolvedIdentifierInspection;

import java.util.List;

/**
 * A plain name that is not declared is an error ("'respons' could not be found, did you spell it
 * right?"), with a fix that changes it to a similarly spelled local, parameter or declaration.
 */
public class C3UnresolvedIdentifierTest extends C3TestCase
{
	/** The inspection only runs when the standard library is indexed; this stands in for it. */
	private static final String BUILTIN = "module std::core::builtin;\n"
		+ "macro void unreachable() @builtin {}\nfn void abort() @builtin {}\n";

	private static final String HEAD = "module http_client;\n"
		+ "struct HttpResponse { int status; }\n"
		+ "int request_count;\n"
		+ "fn int swap_float(int x) => x;\n"
		+ "macro @each(values; @body(value)) { foreach (value : values) @body(value); }\n";

	private List<String> unresolved(String body)
	{
		myFixture.addFileToProject("builtin.c3", BUILTIN);
		return unresolvedWithoutStdlib(body);
	}

	private List<String> unresolvedWithoutStdlib(String body)
	{
		myFixture.enableInspections(new C3UnresolvedIdentifierInspection());
		myFixture.configureByText("http_client.c3", HEAD + body);
		return myFixture.doHighlighting(HighlightSeverity.ERROR).stream()
			.map(HighlightInfo::getDescription)
			.filter(description -> description != null && description.contains("could not be found"))
			.toList();
	}

	public void testMisspelledLocalIsError()
	{
		List<String> errors = unresolved("fn HttpResponse execute()\n{\n    HttpResponse response;\n    return respons;\n}\n");
		assertEquals(List.of("'respons' could not be found, did you spell it right?"), errors);
	}

	public void testDeclaredNamesAreFine()
	{
		List<String> errors = unresolved(
			"fn int execute(int retries, int[] values, int... rest)\n{\n"
				+ "    int total = retries + request_count + swap_float(1) + rest[0];\n"
				+ "    foreach (index, value : values) total += index + value;\n"
				+ "    for (int step = 0; step < 3; step++) total += step;\n"
				+ "    var inferred = total;\n"
				+ "    HttpResponse response = { .status = inferred };\n"
				+ "    if (total < 0) abort();\n"
				+ "    return response.status;\n}\n");
		assertEquals(errors.toString(), 0, errors.size());
	}

	/** Parameters of macros and lambdas and of a body-macro call's trailing block are declared names. */
	public void testMacroLambdaAndTrailingBlockParametersAreFine()
	{
		List<String> errors = unresolved(
			"alias Callback = fn int(int);\n"
				+ "macro int twice(x, #expr, $count) => x + x;\n"
				+ "macro int scaled(int factor, args...) { return factor * 2; }\n"
				+ "fn int execute(int[] values)\n{\n"
				+ "    Callback callback = fn int(int argument) => argument + 1;\n"
				+ "    int total = callback(1);\n"
				+ "    @each(values; int element) { total += element; };\n"
				+ "    return total;\n}\n");
		assertEquals(errors.toString(), 0, errors.size());
	}

	public void testMisspelledNamesInMacroAndTrailingBlockAreErrors()
	{
		List<String> errors = unresolved(
			"macro int twice(value) => valu + value;\n"
				+ "fn int execute(int[] values)\n{\n"
				+ "    int total;\n"
				+ "    @each(values; int element) { total += elemnt; };\n"
				+ "    return total;\n}\n");
		assertEquals(List.of(
			"'valu' could not be found, did you spell it right?",
			"'elemnt' could not be found, did you spell it right?"), errors);
	}

	/** Without an indexed standard library every builtin would be reported, so nothing is. */
	public void testQuietWithoutStandardLibrary()
	{
		List<String> errors = unresolvedWithoutStdlib("fn HttpResponse execute()\n{\n    HttpResponse response;\n    return respons;\n}\n");
		assertEquals(errors.toString(), 0, errors.size());
	}

	public void testFixChangesToTheSimilarLocal()
	{
		unresolved("fn HttpResponse execute(int retries)\n{\n    HttpResponse response;\n    return resp<caret>ons;\n}\n");
		List<String> fixes = myFixture.getAllQuickFixes().stream().map(IntentionAction::getText).toList();
		assertEquals(List.of("Change to 'response'"), fixes);
		myFixture.launchAction(myFixture.findSingleIntention("Change to 'response'"));
		myFixture.checkResult(HEAD + "fn HttpResponse execute(int retries)\n{\n    HttpResponse response;\n    return response;\n}\n");
	}

	public void testFixSuggestsADeclarationOfTheSameModule()
	{
		unresolved("fn int execute()\n{\n    return swap<caret>Float(1);\n}\n");
		myFixture.launchAction(myFixture.findSingleIntention("Change to 'swap_float'"));
		myFixture.checkResult(HEAD + "fn int execute()\n{\n    return swap_float(1);\n}\n");
	}
}
