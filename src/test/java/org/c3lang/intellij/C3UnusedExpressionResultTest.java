package org.c3lang.intellij;

import com.intellij.codeInsight.intention.IntentionAction;
import org.c3lang.intellij.intention.C3UnusedExpressionResultInspection;

import java.util.List;

/** The unused-result warning on `editor.init();` offers both quick fixes; the (void) one discards explicitly. */
public class C3UnusedExpressionResultTest extends C3TestCase
{
	private static final String HEAD =
		"module app;\nstruct Editor { int x; }\nfn Editor* Editor.init(&self) { return self; }\nEditor editor;\n";

	public void testBothFixesOffered()
	{
		myFixture.enableInspections(new C3UnusedExpressionResultInspection());
		myFixture.configureByText("app.c3", HEAD + "fn void f()\n{\n    editor.in<caret>it();\n}\n");
		myFixture.doHighlighting();
		List<String> intentions = myFixture.getAvailableIntentions().stream().map(IntentionAction::getText).toList();
		System.out.println("[TEST] intentions = " + intentions);
		assertTrue(intentions.toString(), intentions.contains("Create local variable"));
		assertTrue(intentions.toString(), intentions.contains("Discard result with (void)"));
	}

	public void testDiscardWithVoid()
	{
		myFixture.enableInspections(new C3UnusedExpressionResultInspection());
		myFixture.configureByText("app.c3", HEAD + "fn void f()\n{\n    editor.in<caret>it();\n}\n");
		myFixture.doHighlighting();
		myFixture.launchAction(myFixture.findSingleIntention("Discard result with (void)"));
		myFixture.checkResult(HEAD + "fn void f()\n{\n    (void)editor.init();\n}\n");
	}

	public void testDiscardedCallIsNotReported()
	{
		myFixture.enableInspections(new C3UnusedExpressionResultInspection());
		myFixture.configureByText("app.c3", HEAD + "fn void f()\n{\n    (void)editor.init();\n}\n");
		long warnings = myFixture.doHighlighting().stream()
			.filter(info -> "Result of this expression is not used".equals(info.getDescription()))
			.count();
		assertEquals(0, warnings);
	}

	private static final String OPTIONAL_HEAD =
		"module app;\nfn int? may() => 1;\nfn void? act() { }\nmacro int? maybe_m() => 1;\n";

	private long unusedWarnings(String body)
	{
		myFixture.enableInspections(new C3UnusedExpressionResultInspection());
		myFixture.configureByText("app.c3", OPTIONAL_HEAD + "fn void f()\n{\n" + body + "}\n");
		return myFixture.doHighlighting().stream()
			.filter(info -> "Result of this expression is not used".equals(info.getDescription()))
			.count();
	}

	/** `may()!!;` and `may()!;` unwrap an `int?` and throw the `int` away. */
	public void testUnwrappedOptionalResultIsReported()
	{
		assertEquals(2, unusedWarnings("    may()!!;\n    may()!;\n"));
	}

	/** Unwrapping a `void?` is pure error handling. */
	public void testUnwrappedVoidOptionalIsFine()
	{
		assertEquals(0, unusedWarnings("    act()!!;\n    act()!;\n"));
	}

	public void testTypedMacroResultIsReported()
	{
		assertEquals(1, unusedWarnings("    maybe_m()!!;\n"));
	}

	public void testCreateLocalVariableForUnwrappedResultDropsTheOptionalMarker()
	{
		myFixture.enableInspections(new C3UnusedExpressionResultInspection());
		myFixture.configureByText("app.c3", OPTIONAL_HEAD + "fn void f()\n{\n    ma<caret>y()!!;\n}\n");
		myFixture.doHighlighting();
		myFixture.launchAction(myFixture.findSingleIntention("Create local variable"));
		String text = myFixture.getEditor().getDocument().getText();
		assertTrue(text, text.contains("    int value = may()!!;"));
	}
}
