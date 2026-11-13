package org.c3lang.intellij;

import com.intellij.codeInsight.intention.IntentionAction;
import org.c3lang.intellij.intention.C3UnresolvedMethodInspection;

import java.util.List;

/** "Create method" on an unresolved method call writes the method next to the struct's other methods. */
public class C3CreateMethodFixTest extends C3TestCase
{
	private static final String CLIENT = "module http_client;\n"
		+ "struct HttpRequest { int method; }\n"
		+ "fn void HttpRequest.init(&self, Allocator allocator) {\n}\n";

	private void apply(String fixName, String client, String body, String expectedClient)
	{
		myFixture.enableInspections(new C3UnresolvedMethodInspection());
		myFixture.addFileToProject("http_client.c3", client);
		myFixture.configureByText("test.c3", "module app;\nimport http_client;\n" + body);
		myFixture.doHighlighting();
		myFixture.launchAction(myFixture.findSingleIntention(fixName));
		myFixture.checkResult("http_client.c3", expectedClient, false);
	}

	/** `req.free(mem);` as a statement: void, and `mem` is an Allocator. */
	public void testStatementCallCreatesAVoidMethod()
	{
		apply("Create method 'HttpRequest.free'", CLIENT,
			"fn void f()\n{\n    HttpRequest req;\n    req.init(mem);\n    req.fr<caret>ee(mem);\n}\n",
			CLIENT.stripTrailing() + "\n\nfn void HttpRequest.free(&self, Allocator allocator) {\n}\n");
	}

	public void testParametersComeFromTheArguments()
	{
		apply("Create method 'HttpRequest.add_header'", CLIENT,
			"fn void f(String name, int* counter)\n{\n    HttpRequest req;\n    double weight;\n"
				+ "    req.add_<caret>header(name, \"value\", 3, weight, true, counter, &weight, 1.5, unknown_call());\n}\n",
			CLIENT.stripTrailing() + "\n\nfn void HttpRequest.add_header(&self, String name, String param2, int param3, double weight, "
				+ "bool param5, int* counter, double* param7, double param8, any param9) {\n}\n");
	}

	public void testReturnTypeComesFromTheDeclaration()
	{
		apply("Create method 'HttpRequest.header_count'", CLIENT,
			"fn void f()\n{\n    HttpRequest req;\n    usz count = req.header_<caret>count();\n}\n",
			CLIENT.stripTrailing() + "\n\nfn usz HttpRequest.header_count(&self) {\n}\n");
	}

	public void testUnwrappedCallCreatesAnOptionalMethod()
	{
		apply("Create method 'HttpRequest.send'", CLIENT,
			"fn void? f()\n{\n    HttpRequest req;\n    req.se<caret>nd()!;\n}\n",
			CLIENT.stripTrailing() + "\n\nfn void? HttpRequest.send(&self) {\n}\n");
	}

	public void testReturnTypeComesFromTheEnclosingFunction()
	{
		apply("Create method 'HttpRequest.url'", CLIENT,
			"fn String f()\n{\n    HttpRequest req;\n    return req.u<caret>rl();\n}\n",
			CLIENT.stripTrailing() + "\n\nfn String HttpRequest.url(&self) {\n}\n");
	}

	/** The method follows the brace style of the struct's file. */
	public void testBracesOnTheirOwnLineLikeTheRestOfTheFile()
	{
		String client = "module http_client;\nstruct HttpRequest { int method; }\nfn void HttpRequest.init(&self)\n{\n}\n"
			+ "fn void unrelated()\n{\n}\n";
		apply("Create method 'HttpRequest.free'", client,
			"fn void f()\n{\n    HttpRequest req;\n    req.fr<caret>ee();\n}\n",
			"module http_client;\nstruct HttpRequest { int method; }\nfn void HttpRequest.init(&self)\n{\n}\n\n"
				+ "fn void HttpRequest.free(&self)\n{\n}\n"
				+ "fn void unrelated()\n{\n}\n");
	}

	/** A field access is no call: nothing to create. */
	public void testNoFixForAFieldAccess()
	{
		myFixture.enableInspections(new C3UnresolvedMethodInspection());
		myFixture.addFileToProject("http_client.c3", CLIENT);
		myFixture.configureByText("test.c3", "module app;\nimport http_client;\nfn void f()\n{\n    HttpRequest req;\n    int value = req.mis<caret>sing;\n}\n");
		myFixture.doHighlighting();
		List<String> fixes = myFixture.getAllQuickFixes().stream().map(IntentionAction::getText).toList();
		assertTrue(fixes.toString(), fixes.stream().noneMatch(fix -> fix.startsWith("Create method")));
	}
}
