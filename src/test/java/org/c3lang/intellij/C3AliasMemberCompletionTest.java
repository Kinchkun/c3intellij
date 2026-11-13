package org.c3lang.intellij;

import java.util.List;

/** Member completion on a value whose type is an alias, also of a generic type of another module. */
public class C3AliasMemberCompletionTest extends C3TestCase
{
	private static final String MAP = "module std::collections::map <Key, Value>;\n"
		+ "struct HashMap { int count; }\n"
		+ "fn HashMap* HashMap.init(&self, Allocator allocator) { return self; }\n"
		+ "fn bool HashMap.set(&map, Key key, Value value) { return true; }\n"
		+ "fn void HashMap.free(&map) {}\n";

	private List<String> complete(String text)
	{
		myFixture.addFileToProject("hashmap.c3", MAP);
		myFixture.configureByText("http_client.c3", text);
		myFixture.completeBasic();
		List<String> lookups = myFixture.getLookupElementStrings();
		System.out.println("[TEST] " + getName() + " lookups = " + lookups);
		return lookups;
	}

	private static void assertMapMembers(List<String> lookups)
	{
		assertNotNull("expected completions", lookups);
		assertTrue("" + lookups, lookups.containsAll(List.of("init", "set", "free")));
	}

	private static final String HEAD = "module http_client;\nimport std::collections::map;\n"
		+ "alias HttpHeaders = HashMap { String, String };\n"
		+ "struct Url { String host; }\n"
		+ "alias Address = Url;\n"
		+ "struct HttpRequest { int method; HttpHeaders headers; HashMap { String, int } counts; Address address; }\n";

	public void testMemberOfAliasedGenericType()
	{
		assertMapMembers(complete(HEAD + "fn void HttpRequest.init(&self, Allocator allocator)\n{\n    self.headers.<caret>\n}\n"));
	}

	public void testMemberOfGenericTypeWrittenDirectly()
	{
		assertMapMembers(complete(HEAD + "fn void HttpRequest.init(&self, Allocator allocator)\n{\n    self.counts.<caret>\n}\n"));
	}

	public void testLocalOfAliasedGenericType()
	{
		assertMapMembers(complete(HEAD + "fn void f()\n{\n    HttpHeaders headers;\n    headers.<caret>\n}\n"));
	}

	public void testMemberOfAliasedStruct()
	{
		List<String> lookups = complete(HEAD + "fn void HttpRequest.init(&self, Allocator allocator)\n{\n    self.address.<caret>\n}\n");
		assertNotNull("expected completions", lookups);
		assertTrue("" + lookups, lookups.contains("host"));
	}

	/** Go to Declaration on a method called through the alias reaches the method of the aliased type. */
	public void testMethodCallThroughAliasResolves()
	{
		myFixture.addFileToProject("hashmap.c3", MAP);
		myFixture.configureByText("http_client.c3",
			HEAD + "fn void HttpRequest.init(&self, Allocator allocator)\n{\n    self.headers.in<caret>it(allocator);\n}\n");
		var reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
		assertNotNull("no reference at the caret", reference);
		var target = reference.resolve();
		assertNotNull("init did not resolve", target);
		assertEquals("hashmap.c3", target.getContainingFile().getName());
	}
}
