package org.c3lang.intellij;


import java.util.List;

/** Verifies struct member completion after `value.`, including the mystd args_test shapes. */
public class C3StructMemberCompletionTest extends C3TestCase
{
	private static final String ARGS =
		"module mystd::args;\n" +
		"struct ArgParser { int _dummy; }\n" +
		"struct ParsedArgs {\n    String   program_name;\n    String[] positionals;\n}\n" +
		"fn ParsedArgs? ArgParser.parse(ArgParser* self, Allocator alloc, String[] argv) { return {}; }\n";

	private List<String> complete(String body)
	{
		myFixture.addFileToProject("args.c3", ARGS);
		myFixture.configureByText("args_test.c3",
			"module mystd::args_test;\nimport mystd::args;\nfn void f() @test\n{\n" + body + "}\n");
		myFixture.completeBasic();
		List<String> lookups = myFixture.getLookupElementStrings();
		System.out.println("[TEST] lookups = " + lookups);
		return lookups;
	}

	private void assertMembers(List<String> lookups)
	{
		assertNotNull("expected completions", lookups);
		assertTrue("program_name missing: " + lookups, lookups.contains("program_name"));
		assertTrue("positionals missing: " + lookups, lookups.contains("positionals"));
	}

	public void testSameModule()
	{
		myFixture.configureByText("same.c3",
			"module app;\nstruct Point { int alpha; int beta; }\nfn void f()\n{\n    Point p;\n    p.<caret>\n}\n");
		myFixture.completeBasic();
		List<String> lookups = myFixture.getLookupElementStrings();
		System.out.println("[TEST] same = " + lookups);
		assertTrue("" + lookups, lookups != null && lookups.contains("alpha"));
	}

	public void testSimpleDecl()
	{
		assertMembers(complete("    ParsedArgs parsed_args;\n    parsed_args.<caret>\n"));
	}

	public void testFollowedByStatement()
	{
		assertMembers(complete(
			"    ParsedArgs parsed_args;\n    parsed_args.<caret>\n    test::eq(parsed_args.program_name, \"x\");\n"));
	}

	public void testInitializedFromCall()
	{
		assertMembers(complete(
			"    ArgParser arg_parser;\n" +
			"    ParsedArgs parsed_args = arg_parser.parse(mem, {\n        \"a\",\n        \"b\"})!!;\n" +
			"    parsed_args.<caret>\n" +
			"    test::eq(parsed_args.program_name, \"program name\");\n"));
	}

	public void testInsideCallArgument()
	{
		assertMembers(complete(
			"    ParsedArgs parsed_args;\n    test::eq(parsed_args.<caret>, \"x\");\n"));
	}

	/** `parsed_args.program_name` navigates to the field of the struct declared in the imported module. */
	public void testMemberResolvesAcrossModules()
	{
		myFixture.addFileToProject("args.c3", ARGS);
		myFixture.configureByText("args_test.c3",
			"module mystd::args_test;\nimport mystd::args;\nfn void f()\n{\n" +
			"    ParsedArgs parsed_args;\n    String s = parsed_args.program_<caret>name;\n}\n");
		var reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
		assertNotNull("no reference at caret", reference);
		var target = reference.resolve();
		assertNotNull("program_name did not resolve", target);
		assertEquals("args.c3", target.getContainingFile().getName());
	}
}
