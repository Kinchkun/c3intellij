package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.c3lang.intellij.intention.C3DuplicateVariableInspection;

import java.util.List;

public class C3DuplicateVariableTest extends BasePlatformTestCase
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
}
