package org.c3lang.intellij;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.psi.PsiElement;
import org.c3lang.intellij.psi.*;

import java.util.Arrays;

/** Navigation cases found by the sweep over the ted sources (C3NavigationSweepTest). */
public class C3NavigationQaTest extends C3TestCase
{
	private PsiElement[] targets()
	{
		PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
		System.out.println("[TEST] targets = " + Arrays.toString(targets));
		return targets;
	}

	private PsiElement single()
	{
		PsiElement[] targets = targets();
		assertEquals("expected exactly one target, got " + Arrays.toString(targets), 1, targets.length);
		return targets[0];
	}

	public void testOwnModuleTypeWinsOverImportedSameName()
	{
		myFixture.addFileToProject("sdl.c3", "module sdl;\nstruct Color { int sdl; }\n");
		myFixture.configureByText("frontend.c3", "module ted::frontend;\nimport sdl;\nstruct Color { char r, g, b, a; }\nconst Col<caret>or WHITE = { 0xff, 0xff, 0xff, 0xff };\n");
		assertEquals("frontend.c3", single().getContainingFile().getName());
	}

	public void testOwnModuleFunctionWinsOverImportedSameName()
	{
		myFixture.addFileToProject("profile.c3", "module profile;\nfn void write_trace(String path) {}\n");
		myFixture.configureByText("main.c3", "module ted;\nimport profile;\nfn void write_trace(String path) {}\nfn void f()\n{\n    write_tr<caret>ace(\"x\");\n}\n");
		assertEquals("main.c3", single().getContainingFile().getName());
	}

	public void testExplicitImportWinsOverStdCore()
	{
		myFixture.addFileToProject("builtin.c3", "module std::core::builtin;\nmacro min(x, ...) @builtin { return x; }\n");
		myFixture.addFileToProject("math.c3", "module mymath;\nmacro min(x, y) { return x; }\n");
		myFixture.configureByText("main.c3", "module ted;\nimport mymath;\nfn void f()\n{\n    int v = mi<caret>n(1, 2);\n}\n");
		assertEquals("math.c3", single().getContainingFile().getName());
	}

	public void testEquallyVisibleCandidatesOfferAChooser()
	{
		myFixture.addFileToProject("a.c3", "module liba;\nfn void foo() {}\n");
		myFixture.addFileToProject("b.c3", "module libb;\nfn void foo() {}\n");
		myFixture.configureByText("main.c3", "module app;\nimport liba, libb;\nfn void f()\n{\n    fo<caret>o();\n}\n");
		assertEquals(2, targets().length);
	}

	public void testQualifiedConstant()
	{
		myFixture.addFileToProject("frontend.c3", "module ted::frontend;\nstruct Color { char r; }\nconst Color WHITE = { 0xff };\n");
		myFixture.configureByText("main.c3", "module ted;\nimport ted::frontend;\nfn void f()\n{\n    frontend::Color c = frontend::WH<caret>ITE;\n}\n");
		assertTrue(single() instanceof C3ConstDeclarationStmt);
	}

	public void testBareEnumConstant()
	{
		myFixture.addFileToProject("argparse.c3", "module argparse;\nenum ParseResult { OK, EXIT }\n");
		myFixture.configureByText("main.c3", "module ted;\nimport argparse;\nfn int f(ParseResult parsed)\n{\n    if (parsed == EX<caret>IT) return 0;\n    return 1;\n}\n");
		PsiElement target = single();
		assertTrue(target.toString(), target instanceof C3EnumConstant);
		assertEquals("EXIT", ((C3EnumConstant) target).getName());
	}

	public void testForeachVariable()
	{
		myFixture.configureByText("main.c3", "module app;\nstruct Line { int n; }\nfn void f(Line[] lines)\n{\n    foreach (row, line : lines)\n    {\n        int v = li<caret>ne.n + row;\n    }\n}\n");
		assertTrue(single() instanceof C3ForeachVar);
	}

	public void testForeachVariableIsNotVisibleAfterTheLoop()
	{
		myFixture.configureByText("main.c3", "module app;\nfn void f(int[] lines)\n{\n    foreach (line : lines) {}\n    int v = li<caret>ne;\n}\n");
		assertEquals(0, targets().length);
	}

	public void testCatchVariable()
	{
		myFixture.configureByText("main.c3", "module app;\nfn void? load() {}\nfn void f()\n{\n    if (catch err = load())\n    {\n        return;\n    }\n    if (catch err = load()) use(e<caret>rr);\n}\nfn void use(fault e) {}\n");
		assertTrue(single() instanceof C3CatchUnwrap);
	}

	public void testTryVariable()
	{
		myFixture.configureByText("main.c3", "module app;\nfn int? load() { return 1; }\nfn void f()\n{\n    if (try v = load()) use(<caret>v);\n}\nfn void use(int e) {}\n");
		assertTrue(single() instanceof C3TryUnwrap);
	}

	public void testVarDeclaration()
	{
		myFixture.configureByText("main.c3", "module app;\nmacro @m(#call)\n{\n    var result = #call;\n    return res<caret>ult;\n}\n");
		assertTrue(single() instanceof C3VarDecl);
	}

	public void testTypedForeachVariableMemberAccess()
	{
		myFixture.configureByText("main.c3", "module app;\nstruct Line { int n; }\nfn void f(Line[] lines)\n{\n    foreach (Line line : lines)\n    {\n        int v = line.<caret>n;\n    }\n}\n");
		assertTrue(single() instanceof C3StructMemberDeclaration);
	}

	/** A method declared with its type resolves where the type's module is not imported, as c3c allows. */
	public void testMethodOfTypeFromUnimportedModule()
	{
		myFixture.addFileToProject("list.c3", "module std::collections::list <Type>;\nstruct List { usz size; Type[] entries; }\nfn usz List.len(&self) { return self.size; }\n");
		myFixture.addFileToProject("editor.c3", "module ted::editor;\nimport std::collections::list;\nstruct Editor { List { String } lines; }\n");
		myFixture.configureByText("editor_test.c3", "module ted::editor_test;\nimport ted::editor;\nfn void f()\n{\n    Editor editor;\n    usz n = editor.lines.l<caret>en();\n}\n");
		assertTrue(single().getText(), single().getText().contains("List.len"));
	}

	public void testMacroOfImpliesBody()
	{
		myFixture.configureByText("main.c3", "module app;\nmacro void @pool(; @body)\n{\n    @body();\n}\nfn void f() => @po<caret>ol()\n{\n}\n");
		assertTrue(single().getText(), single().getText().contains("@pool"));
	}

	public void testModulePathNavigatesToTheModule()
	{
		myFixture.addFileToProject("io.c3", "module std::io;\nfn void printfn(String s) {}\n");
		myFixture.configureByText("main.c3", "module app;\nimport std::io;\nfn void f()\n{\n    i<caret>o::printfn(\"x\");\n}\n");
		PsiElement target = single();
		assertTrue(target.toString(), target instanceof C3Module);
		assertEquals("io.c3", target.getContainingFile().getName());
	}

	public void testTypeBehindModulePathStillNavigatesToTheType()
	{
		myFixture.addFileToProject("frontend.c3", "module ted::frontend;\nstruct Window { int w; }\n");
		myFixture.configureByText("main.c3", "module ted;\nimport ted::frontend;\nfrontend::Win<caret>dow window;\n");
		assertTrue(single() instanceof C3TypeName);
	}
}
