package org.c3lang.intellij;

import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class C3FindUsagesTest extends BasePlatformTestCase
{
	private static final String SRC =
		"module app;\n" +
		"\n" +
		"enum CurlOption : int\n" +
		"{\n" +
		"    WRITEDATA,\n" +
		"    URL,\n" +
		"}\n" +
		"\n" +
		"fn void foo()\n" +
		"{\n" +
		"    CurlOption a = CurlOption.WRITEDATA;\n" +
		"    CurlOption b = CurlOption.URL;\n" +
		"}\n";

	/** Usages of the enum type itself (worked before — guards against regressions). */
	public void testEnumTypeUsages()
	{
		myFixture.configureByText("a.c3", SRC.replace("enum CurlOption", "enum Curl<caret>Option"));
		PsiElement target = myFixture.getElementAtCaret();
		assertEquals(4, myFixture.findUsages(target).size());
	}

	/** Usages of an enum constant via {@code Type.CONSTANT} access (the bug this test pins down). */
	public void testEnumConstantUsages()
	{
		myFixture.configureByText("a.c3", SRC.replace("    WRITEDATA,", "    WRITE<caret>DATA,"));
		PsiElement target = myFixture.getElementAtCaret();
		assertEquals(1, myFixture.findUsages(target).size());
	}
}
