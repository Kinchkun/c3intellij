package org.c3lang.intellij;

import java.util.List;

/** After `.` in a designated initializer the fields of the initialized struct are offered. */
public class C3InitializerCompletionTest extends C3TestCase
{
	private static final String HEAD = "module test_server;\n"
		+ "struct Header { String name; String value; }\n"
		+ "struct Response { int status; String body; Header header; }\n"
		+ "struct Request { String path; }\n"
		+ "alias Handler = fn Response(Request* request);\n"
		+ "struct Server { int port; }\n"
		+ "fn void Server.start(&self, Handler handler) {}\n"
		+ "fn void send(Response response, int flags) {}\n"
		+ "fn void free(void* ptr) {}\n";

	private List<String> complete(String body)
	{
		myFixture.configureByText("test_server.c3", HEAD + body);
		myFixture.completeBasic();
		List<String> lookups = myFixture.getLookupElementStrings();
		System.out.println("[TEST] " + getName() + " lookups = " + lookups);
		return lookups;
	}

	private static void assertResponseFields(List<String> lookups)
	{
		assertNotNull("expected completions", lookups);
		assertEquals(List.of("status", "body", "header"), lookups);
	}

	public void testLocalDeclaration()
	{
		assertResponseFields(complete("fn void f()\n{\n    Response response = { .<caret> };\n}\n"));
	}

	public void testReturnInFunction()
	{
		assertResponseFields(complete("fn Response f()\n{\n    return {\n        .<caret>\n    };\n}\n"));
	}

	public void testReturnInLambdaAfterAnotherField()
	{
		List<String> lookups = complete("fn void f()\n{\n    Server server;\n"
			+ "    server.start(fn Response(Request* request) {\n        return {\n            .status = 200,\n            .<caret>\n        };\n    });\n}\n");
		assertNotNull("expected completions", lookups);
		// `status` is already set.
		assertEquals(List.of("body", "header"), lookups);
	}

	public void testAssignment()
	{
		assertResponseFields(complete("fn void f()\n{\n    Response response;\n    response = { .<caret> };\n}\n"));
	}

	public void testCompoundLiteral()
	{
		assertResponseFields(complete("fn void f()\n{\n    send((Response){ .<caret> }, 0);\n}\n"));
	}

	public void testCallArgument()
	{
		assertResponseFields(complete("fn void f()\n{\n    send({ .<caret> }, 0);\n}\n"));
	}

	public void testNestedInitializer()
	{
		List<String> lookups = complete("fn Response f()\n{\n    return { .status = 200, .header = { .<caret> } };\n}\n");
		assertNotNull("expected completions", lookups);
		assertEquals(List.of("name", "value"), lookups);
	}

	public void testTypedPrefix()
	{
		List<String> lookups = complete("fn Response f()\n{\n    return { .st<caret> };\n}\n");
		if (lookups == null)
		{
			assertTrue(myFixture.getEditor().getDocument().getText().contains(".status"));
		}
		else
		{
			assertEquals(List.of("status"), lookups);
		}
	}

	public void testInsertsTheAssignment()
	{
		complete("fn Response f()\n{\n    return { .bo<caret> };\n}\n");
		myFixture.checkResult(HEAD + "fn Response f()\n{\n    return { .body = <caret> };\n}\n");
	}

	/** A member access in a value is no designator. */
	public void testMemberAccessInAValueIsNotADesignator()
	{
		List<String> lookups = complete("fn Response f(Request* request)\n{\n    return { .body = request.<caret> };\n}\n");
		assertNotNull(lookups);
		assertEquals(List.of("path"), lookups);
	}

	/** The struct of the user's test: declared in another module, returned from a lambda. */
	public void testStructOfAnImportedModule()
	{
		myFixture.addFileToProject("server.c3", HEAD.replace("module test_server;", "module http_client::test_server;"));
		myFixture.configureByText("basic_test.c3", "module c3_workspace;\nimport http_client::test_server;\n"
			+ "fn void can_make_a_get_call()\n{\n    Server server;\n"
			+ "    server.start(fn Response(Request* request) {\n        return {\n            .status = 200,\n            .<caret>\n        };\n    });\n}\n");
		myFixture.completeBasic();
		assertEquals(List.of("body", "header"), myFixture.getLookupElementStrings());
	}
}
