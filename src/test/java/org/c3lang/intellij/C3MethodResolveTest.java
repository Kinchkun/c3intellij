package org.c3lang.intellij;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.c3lang.intellij.psi.C3AccessIdent;
import org.c3lang.intellij.psi.C3FuncDefinition;

public class C3MethodResolveTest extends BasePlatformTestCase
{
	private PsiElement resolveSetOpt(String body)
	{
		myFixture.configureByText("app.c3",
			"module app;\n"
				+ "enum CurlOption : int { WRITEDATA, URL }\n"
				+ "struct HttpClient { int fd; }\n"
				+ "fn void HttpClient.set_opt(&self, CurlOption opt) {}\n"
				+ "fn void HttpClient.use(&self)\n{\n" + body + "}\n");

		int offset = myFixture.getFile().getText().indexOf("set_o") + 2;
		// find the call-site access ident (skip the definition's func name occurrence)
		C3AccessIdent access = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(
			myFixture.getEditor().getCaretModel().getOffset()), C3AccessIdent.class, false);
		System.out.println("[TEST] access ident = " + (access != null ? access.getNameIdent() : "null"));
		PsiReference ref = access != null ? access.getReference() : null;
		PsiElement resolved = ref != null ? ref.resolve() : null;
		System.out.println("[TEST] resolved = " + resolved + " | "
			+ (resolved != null ? resolved.getText().replace("\n", " ") : "null"));
		return resolved;
	}

	public void testMethodCallNoArgsResolves()
	{
		PsiElement r = resolveSetOpt("    self.set_o<caret>pt(WRITEDATA);\n");
		assertTrue("self.set_opt(...) should resolve to the method definition",
			r instanceof C3FuncDefinition || PsiTreeUtil.getParentOfType(r, C3FuncDefinition.class) != null
				|| (r != null && r.getText().contains("set_opt")));
	}

	public void testMethodCallWithDottedArgResolves()
	{
		// The dotted argument (CurlOption.WRITEDATA) is what breaks the text-split ident extraction.
		PsiElement r = resolveSetOpt("    self.set_o<caret>pt(CurlOption.WRITEDATA);\n");
		assertNotNull("self.set_opt(CurlOption.WRITEDATA) should still resolve", r);
		assertTrue(r.getText().contains("set_opt"));
	}

	public void testMethodCallOnLocalVariableResolves()
	{
		myFixture.configureByText("app.c3",
			"module app;\n"
				+ "enum CurlOption : int { WRITEDATA, URL }\n"
				+ "struct HttpClient { int fd; }\n"
				+ "fn void HttpClient.set_opt(&self, CurlOption opt) {}\n"
				+ "fn void run()\n{\n    HttpClient c;\n    c.set_o<caret>pt(WRITEDATA);\n}\n");

		var access = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(
			myFixture.getEditor().getCaretModel().getOffset()), C3AccessIdent.class, false);
		PsiReference ref = access != null ? access.getReference() : null;
		PsiElement resolved = ref != null ? ref.resolve() : null;
		assertNotNull("c.set_opt should resolve to the method", resolved);
		assertTrue(resolved.getText().contains("set_opt"));
	}
}
