package org.c3lang.intellij;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;

import java.util.List;

/** Type completion: one entry per type, shown and inserted by its bare name. */
public class C3TypeCompletionTest extends C3TestCase
{
	private static final String CURL =
		"module curl;\nenum CurlResult : int\n{\n\tOK,\n\tFAILED_INIT,\n\tOUT_OF_MEMORY,\n}\n"
			+ "constdef CurlResolveOption : int\n{\n\tFIRST = 1,\n\tSECOND = 2,\n}\n";

	private LookupElement[] complete(String imports, String body)
	{
		myFixture.addFileToProject("curl.c3", CURL);
		myFixture.configureByText("app.c3", "module app;\n" + imports + "fn void f()\n{\n" + body + "}\n");
		return myFixture.completeBasic();
	}

	private static String presentation(LookupElement element)
	{
		LookupElementPresentation presentation = new LookupElementPresentation();
		element.renderElement(presentation);
		return presentation.getItemText() + " [" + presentation.getTypeText() + "]";
	}

	/** Enum and constdef constants are indexed under their type's name; they are not types. */
	public void testOneEntryPerType()
	{
		LookupElement[] elements = complete("import curl;\n", "    CurlRes<caret>\n");
		assertNotNull("expected a popup", elements);
		List<String> shown = java.util.Arrays.stream(elements).map(C3TypeCompletionTest::presentation).sorted().toList();
		assertEquals(List.of("CurlResolveOption [curl]", "CurlResult [curl]"), shown);
	}

	public void testInsertsTheBareNameWhenTheModuleIsImported()
	{
		complete("import curl;\n", "    CurlResu<caret>\n");
		myFixture.checkResult("module app;\nimport curl;\nfn void f()\n{\n    CurlResult<caret>\n}\n");
	}

	public void testImportsTheModuleAndInsertsTheBareName()
	{
		complete("", "    CurlResu<caret>\n");
		String text = myFixture.getEditor().getDocument().getText();
		assertTrue(text, text.contains("import curl;"));
		assertTrue(text, text.contains("    CurlResult\n"));
	}
}
