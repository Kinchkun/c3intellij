package org.c3lang.intellij;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class C3ResolveTest extends BasePlatformTestCase
{
	private static final String CURL =
		"module curl;\n" +
		"\n" +
		"enum CurlOption : int\n" +
		"{\n" +
		"    WRITEDATA,\n" +
		"    URL,\n" +
		"}\n";

	/** An imported type from another module must resolve from a project file. */
	public void testImportedTypeResolves()
	{
		myFixture.addFileToProject("curl.c3", CURL);
		myFixture.configureByText("app.c3",
			"module app;\n" +
			"import curl;\n" +
			"fn void foo()\n" +
			"{\n" +
			"    Curl<caret>Option x = CurlOption.WRITEDATA;\n" +
			"}\n");
		PsiReference ref = myFixture.getReferenceAtCaretPosition();
		assertNotNull("expected a reference on the type usage", ref);
		PsiElement resolved = ref.resolve();
		System.out.println("[TEST] CurlOption resolved to: " + resolved);
		assertNotNull("imported type CurlOption should resolve across modules", resolved);
	}
}
