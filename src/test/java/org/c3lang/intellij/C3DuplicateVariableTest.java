package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import org.c3lang.intellij.intention.C3DuplicateVariableInspection;

import java.util.List;

public class C3DuplicateVariableTest extends C3TestCase
{
	private long duplicateErrors(String body)
	{
		myFixture.enableInspections(new C3DuplicateVariableInspection());
		myFixture.configureByText("a.c3", "module app;\n\nfn void f()\n{\n" + body + "}\n");
		List<HighlightInfo> infos = myFixture.doHighlighting(HighlightSeverity.ERROR);
		return infos.stream().filter(i -> i.getDescription() != null
			&& i.getDescription().contains("already defined in the scope")).count();
	}

	public void testDuplicateInSameScopeIsError()
	{
		assertEquals(1, duplicateErrors("    int a = 1;\n    int a = 2;\n"));
	}

	public void testDistinctNamesAreFine()
	{
		assertEquals(0, duplicateErrors("    int a = 1;\n    int b = 2;\n"));
	}

	public void testShadowingInNestedScopeIsAllowed()
	{
		assertEquals(0, duplicateErrors("    int a = 1;\n    {\n        int a = 2;\n    }\n"));
	}

	/** Each `case` (and `default`) body is its own scope, as in the compiler, so cases may reuse a name. */
	public void testEachSwitchCaseIsItsOwnScope()
	{
		assertEquals(0, duplicateErrors("    int k = 1;\n    switch (k) {\n"
			+ "        case 1:\n            String value = \"a\";\n"
			+ "        case 2:\n            int value = 2;\n"
			+ "        default:\n            int value = 3;\n    }\n"));
	}

	public void testDuplicateWithinOneCaseIsError()
	{
		assertEquals(1, duplicateErrors("    int k = 1;\n    switch (k) {\n"
			+ "        case 1:\n            int value = 1;\n            int value = 2;\n    }\n"));
	}

	public void testCaseDeclarationShadowingAnOuterOneIsAllowed()
	{
		assertEquals(0, duplicateErrors("    int value = 0;\n    int k = 1;\n    switch (k) {\n"
			+ "        case 1:\n            int value = 1;\n    }\n"));
	}
}
