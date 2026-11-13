package org.c3lang.intellij;


import java.util.List;

/** Verifies enum-constant completion after `EnumType.` (across modules and with a typed prefix). */
public class C3EnumCompletionTest extends C3TestCase
{
	private static final String CURL =
		"module curl;\nenum CurlOption : int\n{\n    WRITEDATA,\n    URL,\n}\n";

	private List<String> complete(String body)
	{
		myFixture.addFileToProject("curl.c3", CURL);
		myFixture.configureByText("app.c3",
			"module app;\nimport curl;\nfn void f()\n{\n" + body + "}\n");
		myFixture.completeBasic();
		List<String> lookups = myFixture.getLookupElementStrings();
		System.out.println("[TEST] lookups = " + lookups);
		return lookups;
	}

	public void testOffersEnumConstantsAfterDot()
	{
		List<String> lookups = complete("    CurlOption x = CurlOption.<caret>\n");
		assertNotNull("expected completions after CurlOption.", lookups);
		assertTrue("should offer WRITEDATA, got: " + lookups, lookups.contains("WRITEDATA"));
		assertTrue("should offer URL, got: " + lookups, lookups.contains("URL"));
	}

	public void testFiltersByTypedPrefix()
	{
		List<String> lookups = complete("    CurlOption x = CurlOption.WR<caret>\n");
		if (lookups == null)
		{
			// Single match was auto-inserted.
			assertTrue(myFixture.getEditor().getDocument().getText().contains("CurlOption.WRITEDATA"));
		}
		else
		{
			assertTrue("WRITEDATA should match prefix WR, got: " + lookups, lookups.contains("WRITEDATA"));
			assertFalse("URL should not match prefix WR, got: " + lookups, lookups.contains("URL"));
			// No type completions should leak in after `EnumType.` despite the dummy parse.
			assertTrue("only enum constants expected, got: " + lookups,
				lookups.stream().noneMatch(s -> s.contains("::")));
		}
	}

	public void testNoEnumConstantsOnValueMemberAccess()
	{
		// `x.` is value member access, not `Type.` — must not offer enum constants.
		List<String> lookups = complete("    CurlOption x = CurlOption.WRITEDATA;\n    x.<caret>\n");
		if (lookups != null)
		{
			assertFalse("enum constants must not appear on value access", lookups.contains("WRITEDATA"));
		}
	}

	private static final String RESULT =
		"module curl;\nenum CurlResult : int\n{\n\tOK,\n\tUNSUPPORTED_PROTOCOL,\n\tOBSOLETE20,\n"
			+ "\tABORTED_BY_CALLBACK,\n\tOBSOLETE24,\n\tOUT_OF_MEMORY,\n}\n";

	private List<String> completeResult(String body)
	{
		myFixture.addFileToProject("result.c3", RESULT);
		myFixture.configureByText("app.c3", "module app;\nimport curl;\nfn void f(CurlResult result)\n{\n" + body + "}\n");
		myFixture.completeBasic();
		return myFixture.getLookupElementStrings();
	}

	/** Constants starting with the typed text come first, in declaration order; `OK` is not buried under `OBSOLETE…`. */
	public void testPrefixMatchesFirstInDeclarationOrder()
	{
		assertEquals(
			List.of("OK", "OBSOLETE20", "OBSOLETE24", "OUT_OF_MEMORY", "UNSUPPORTED_PROTOCOL", "ABORTED_BY_CALLBACK"),
			completeResult("    if (result == curl::CurlResult.O<caret>) {\n    }\n"));
	}

	public void testAllConstantsInDeclarationOrder()
	{
		assertEquals(
			List.of("OK", "UNSUPPORTED_PROTOCOL", "OBSOLETE20", "ABORTED_BY_CALLBACK", "OBSOLETE24", "OUT_OF_MEMORY"),
			completeResult("    if (result == curl::CurlResult.<caret>) {\n    }\n"));
	}
}
