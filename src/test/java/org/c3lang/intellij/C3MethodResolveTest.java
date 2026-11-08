package org.c3lang.intellij;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3AccessIdent;
import org.c3lang.intellij.psi.C3FuncDefinition;

public class C3MethodResolveTest extends C3TestCase
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

	private static final String LIST =
		"module std::collections::list <Type>;\n"
			+ "struct List { usz size; Type[] entries; }\n"
			+ "fn Type[] List.array_view(&self)\n{\n    return self.entries[:self.size];\n}\n";

	private PsiElement resolveAccessAtCaret()
	{
		C3AccessIdent access = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(
			myFixture.getEditor().getCaretModel().getOffset()), C3AccessIdent.class, false);
		PsiReference ref = access != null ? access.getReference() : null;
		PsiElement resolved = ref != null ? ref.resolve() : null;
		System.out.println("[TEST] resolved = " + resolved);
		return resolved;
	}

	/** `editor.lines.array_view()`: a method on a generic-typed member of a global, all across modules. */
	public void testMethodOnGenericTypedMemberOfGlobalResolves()
	{
		myFixture.addFileToProject("list.c3", LIST);
		myFixture.addFileToProject("editor.c3",
			"module app::editor;\nimport std::collections::list;\nstruct Editor { List { String } lines; int first_line; }\n");
		myFixture.configureByText("main.c3",
			"module app;\nimport std::collections::list, app::editor;\nEditor editor;\n"
				+ "fn void f()\n{\n    String[] content = editor.lines.array_<caret>view();\n}\n");
		PsiElement resolved = resolveAccessAtCaret();
		assertNotNull("editor.lines.array_view() should resolve to List.array_view", resolved);
		assertTrue(resolved.getText(), resolved.getText().contains("List.array_view"));
	}

	/** `List { String } l; l.array_view()`: a method on a local of a generic type. */
	public void testMethodOnGenericTypedLocalResolves()
	{
		myFixture.addFileToProject("list.c3", LIST);
		myFixture.configureByText("main.c3",
			"module app;\nimport std::collections::list;\n"
				+ "fn void f()\n{\n    List { String } l;\n    String[] content = l.array_<caret>view();\n}\n");
		PsiElement resolved = resolveAccessAtCaret();
		assertNotNull("l.array_view() should resolve to List.array_view", resolved);
		assertTrue(resolved.getText(), resolved.getText().contains("List.array_view"));
	}

	/** Member completion through a generic-typed member: `editor.lines.` offers List's fields. */
	public void testMemberCompletionThroughGenericTypedMember()
	{
		myFixture.addFileToProject("list.c3", LIST);
		myFixture.addFileToProject("editor.c3",
			"module app::editor;\nimport std::collections::list;\nstruct Editor { List { String } lines; }\n");
		myFixture.configureByText("main.c3",
			"module app;\nimport std::collections::list, app::editor;\nEditor editor;\n"
				+ "fn void f()\n{\n    editor.lines.<caret>\n}\n");
		myFixture.completeBasic();
		java.util.List<String> lookups = myFixture.getLookupElementStrings();
		System.out.println("[TEST] lookups = " + lookups);
		assertTrue("size missing: " + lookups, lookups != null && lookups.contains("size"));
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

	/** `size.h` resolves to the declaration `int w, h;`, the second of its names. */
	public void testSecondNameOfMultiNameFieldResolves()
	{
		myFixture.configureByText("main.c3",
			"module app;\nstruct Size { int w, h; }\nfn void f(Size size)\n{\n    int a = size.<caret>h;\n}\n");
		PsiElement resolved = resolveAccessAtCaret();
		assertNotNull("size.h should resolve to the field declaration", resolved);
		assertTrue(resolved.getText(), resolved.getText().contains("int w, h;"));
	}
}
