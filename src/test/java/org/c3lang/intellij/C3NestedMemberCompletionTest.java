package org.c3lang.intellij;

import java.util.List;

/** Member completion on a parameter inside call arguments that follow another member access. */
public class C3NestedMemberCompletionTest extends C3TestCase
{
	private static final String HEAD = "module http_client;\n"
		+ "struct Url { String host; }\n"
		+ "struct HttpClient { void* curl; }\n"
		+ "struct HttpRequest { int method; Url url; }\n"
		+ "fn int easy_setopt(void* curl, int option, ...) => 0;\n"
		+ "macro @ok(#call) => #call;\n"
		+ "fn void? HttpClient.init(&self) {}\n";

	private List<String> complete(String body)
	{
		myFixture.configureByText("http_client.c3",
			HEAD + "fn void? HttpClient.execute(&self, HttpRequest* request)\n{\n" + body + "}\n");
		myFixture.completeBasic();
		List<String> lookups = myFixture.getLookupElementStrings();
		System.out.println("[TEST] " + getName() + " lookups = " + lookups);
		return lookups;
	}

	private static void assertRequestMembers(List<String> lookups)
	{
		assertNotNull("expected completions", lookups);
		assertTrue("url missing: " + lookups, lookups.contains("url"));
		assertTrue("method missing: " + lookups, lookups.contains("method"));
		assertFalse("methods of the other receiver offered: " + lookups, lookups.contains("execute"));
	}

	public void testStatement()
	{
		assertRequestMembers(complete("    request.<caret>\n"));
	}

	public void testOnlyArgument()
	{
		assertRequestMembers(complete("    easy_setopt(request.<caret>);\n"));
	}

	public void testAfterAnotherMemberAccess()
	{
		assertRequestMembers(complete("    easy_setopt(self.curl, 1, request.<caret>);\n"));
	}

	public void testNestedInMacroCallWithRethrow()
	{
		assertRequestMembers(complete("    @ok(easy_setopt(self.curl, 1, request.<caret>))!;\n    @ok(easy_setopt(self.curl, 2, \"\"))!;\n"));
	}

	public void testSecondLevelMember()
	{
		List<String> lookups = complete("    easy_setopt(self.curl, 1, request.url.<caret>);\n");
		assertNotNull(lookups);
		assertTrue("host missing: " + lookups, lookups.contains("host"));
	}

	public void testLocalReceiverAfterAnotherMemberAccess()
	{
		List<String> lookups = complete("    Url target;\n    easy_setopt(self.curl, 1, request.method, target.<caret>);\n");
		assertNotNull(lookups);
		assertTrue("host missing: " + lookups, lookups.contains("host"));
		assertFalse("" + lookups, lookups.contains("method"));
	}

	public void testReceiverOnTheRightOfAnAssignment()
	{
		assertRequestMembers(complete("    int method;\n    method = self.curl ? request.<caret> : 0;\n"));
	}
}
