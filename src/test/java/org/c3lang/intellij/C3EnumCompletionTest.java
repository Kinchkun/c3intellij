package org.c3lang.intellij;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/** Verifies enum-constant completion after `EnumType.` (across modules and with a typed prefix). */
public class C3EnumCompletionTest extends BasePlatformTestCase
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
}
