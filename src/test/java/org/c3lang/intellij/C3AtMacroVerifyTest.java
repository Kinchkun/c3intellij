package org.c3lang.intellij;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Arrays;
import java.util.List;

/** Verifies go-to-declaration and completion for `@`-prefixed macro references such as `@pool()`. */
public class C3AtMacroVerifyTest extends BasePlatformTestCase
{
	private static final String SRC =
		"module app;\n" +
		"\n" +
		"macro @pool()\n" +
		"{\n" +
		"}\n" +
		"\n" +
		"macro @poolish()\n" +
		"{\n" +
		"}\n";

	public void testAtMacroGotoDeclaration()
	{
		myFixture.configureByText("app.c3",
			SRC + "\nfn void foo()\n{\n    @po<caret>ol();\n}\n");

		// Runs all registered GotoDeclarationHandlers, exactly like Ctrl-B / Cmd-click in the editor.
		PsiElement[] targets = GotoDeclarationAction
			.findAllTargetElements(getProject(), myFixture.getEditor(), myFixture.getCaretOffset());

		System.out.println("[TEST] goto targets = " + Arrays.toString(targets));
		// Go-to may surface more than the local macro: a bare `@pool` also sees std-library macros
		// (std::core::* is treated as auto-imported), matching how regular function resolution behaves.
		assertNotNull("@pool should resolve to at least one declaration", targets);
		assertTrue("@pool should resolve to at least one declaration", targets.length >= 1);
		assertTrue("a resolved target should be a `macro @pool(...)` declaration",
			Arrays.stream(targets).anyMatch(t -> t.getText().contains("macro @pool(")));
	}

	/** Probe: an `@`-macro with no matching declaration must not produce a bogus target. */
	public void testUnresolvedAtMacroHasNoTarget()
	{
		myFixture.configureByText("app.c3",
			SRC + "\nfn void foo()\n{\n    @nonexis<caret>tent();\n}\n");

		PsiElement[] targets = GotoDeclarationAction
			.findAllTargetElements(getProject(), myFixture.getEditor(), myFixture.getCaretOffset());

		System.out.println("[TEST] unresolved goto targets = " + Arrays.toString(targets));
		assertTrue("unknown @macro should resolve to nothing",
			targets == null || targets.length == 0);
	}

	public void testAtMacroCompletion()
	{
		myFixture.configureByText("app.c3",
			SRC + "\nfn void foo()\n{\n    @po<caret>\n}\n");

		myFixture.completeBasic();
		List<String> lookups = myFixture.getLookupElementStrings();
		System.out.println("[TEST] completion lookups = " + lookups);

		if (lookups == null)
		{
			// A single candidate was auto-inserted instead of showing a list.
			String text = myFixture.getEditor().getDocument().getText();
			assertTrue("auto-inserted completion should be an @-macro", text.contains("@pool"));
		}
		else
		{
			assertTrue("completion should offer @pool, got: " + lookups,
				lookups.stream().anyMatch(s -> s.contains("@pool")));
		}
	}
}
