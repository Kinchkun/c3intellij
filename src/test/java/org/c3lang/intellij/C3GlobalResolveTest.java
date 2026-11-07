package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.c3lang.intellij.intention.C3UnusedDeclarationInspection;
import org.c3lang.intellij.psi.C3GlobalName;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3Parameter;

import java.util.List;

/**
 * A module-level global ({@code Editor editor;}) resolves from its usages, in the same file, in
 * another file of the module and from an imported module; Find Usages, rename and member
 * completion work on it; locals and parameters shadow it.
 */
public class C3GlobalResolveTest extends C3TestCase
{
	private static final String EDITOR = "struct Editor { int x; int y; }\n";
	private static final String USE = "fn void f()\n{\n    int v = editor.x;\n    editor.y = v;\n}\n";
	private static final String USE_WITH_CARET = USE.replace("int v = editor.x", "int v = edi<caret>tor.x");

	private PsiElement resolveAtCaret()
	{
		PsiReference reference = myFixture.getReferenceAtCaretPosition();
		assertNotNull("expected a reference at the caret", reference);
		PsiElement resolved = reference.resolve();
		System.out.println("[TEST] resolved to " + resolved);
		return resolved;
	}

	private static void assertGlobal(PsiElement resolved, String name)
	{
		assertTrue("expected the global '" + name + "', got " + resolved, resolved instanceof C3GlobalName);
		assertEquals(name, ((C3GlobalName) resolved).getName());
	}

	public void testResolvesInSameFile()
	{
		myFixture.configureByText("app.c3", "module app;\n" + EDITOR + "Editor editor;\n" + USE_WITH_CARET);
		assertGlobal(resolveAtCaret(), "editor");
	}

	public void testResolvesInOtherFileOfSameModule()
	{
		myFixture.addFileToProject("state.c3", "module app;\n" + EDITOR + "Editor editor;\n");
		myFixture.configureByText("app.c3", "module app;\n" + USE_WITH_CARET);
		assertGlobal(resolveAtCaret(), "editor");
	}

	public void testResolvesFromImportedModule()
	{
		myFixture.addFileToProject("state.c3", "module state;\n" + EDITOR + "Editor editor;\n");
		myFixture.configureByText("app.c3", "module app;\nimport state;\n" + USE_WITH_CARET);
		assertGlobal(resolveAtCaret(), "editor");
	}

	public void testNotVisibleWithoutImport()
	{
		myFixture.addFileToProject("state.c3", "module state;\n" + EDITOR + "Editor editor;\n");
		myFixture.configureByText("app.c3", "module app;\n" + USE_WITH_CARET);
		assertNull("a global of a module that is not imported must not resolve", resolveAtCaret());
	}

	public void testLocalShadowsGlobal()
	{
		myFixture.configureByText("app.c3",
			"module app;\nint editor;\nfn void f()\n{\n    int editor = 1;\n    int v = edi<caret>tor;\n}\n");
		assertTrue(resolveAtCaret() instanceof C3LocalDeclAfterType);
	}

	public void testParameterShadowsGlobal()
	{
		myFixture.configureByText("app.c3",
			"module app;\nint editor;\nfn void f(int editor)\n{\n    int v = edi<caret>tor;\n}\n");
		assertTrue(resolveAtCaret() instanceof C3Parameter);
	}

	public void testPlainUseResolves()
	{
		myFixture.configureByText("app.c3",
			"module app;\nint counter;\nfn void f()\n{\n    int v = coun<caret>ter;\n}\n");
		assertGlobal(resolveAtCaret(), "counter");
	}

	/** `use(editor)`: a global passed as a call argument (an argument is also a path-name provider). */
	public void testGlobalAsCallArgumentResolves()
	{
		myFixture.configureByText("app.c3",
			"module app;\n" + EDITOR + "Editor editor;\nfn void use(Editor e) {}\nfn void f()\n{\n    use(edi<caret>tor);\n}\n");
		assertGlobal(resolveAtCaret(), "editor");
	}

	public void testEachNameOfAMultiDeclarationResolves()
	{
		myFixture.configureByText("app.c3", "module app;\nint a, b;\nfn void f()\n{\n    int v = <caret>b;\n}\n");
		assertGlobal(resolveAtCaret(), "b");
	}

	public void testFindUsagesFromDeclaration()
	{
		myFixture.configureByText("app.c3", "module app;\n" + EDITOR + "Editor edi<caret>tor;\n" + USE);
		PsiElement target = myFixture.getElementAtCaret();
		assertGlobal(target, "editor");
		assertEquals(2, myFixture.findUsages(target).size());
	}

	public void testFindUsagesFromUsage()
	{
		myFixture.configureByText("app.c3", "module app;\n" + EDITOR + "Editor editor;\n" + USE_WITH_CARET);
		PsiElement target = myFixture.getElementAtCaret();
		assertGlobal(target, "editor");
		assertEquals(2, myFixture.findUsages(target).size());
	}

	public void testFindUsagesAcrossFiles()
	{
		myFixture.addFileToProject("other.c3", "module app;\nfn void g()\n{\n    editor.x = 1;\n}\n");
		myFixture.configureByText("app.c3", "module app;\n" + EDITOR + "Editor edi<caret>tor;\n" + USE);
		assertEquals(3, myFixture.findUsages(myFixture.getElementAtCaret()).size());
	}

	public void testRename()
	{
		myFixture.configureByText("app.c3", "module app;\n" + EDITOR + "Editor editor;\n" + USE_WITH_CARET);
		myFixture.renameElementAtCaret("ed");
		String text = myFixture.getEditor().getDocument().getText();
		assertEquals("module app;\n" + EDITOR + "Editor ed;\n" + USE.replace("editor", "ed"), text);
	}

	public void testMemberCompletion()
	{
		myFixture.configureByText("app.c3",
			"module app;\n" + EDITOR + "Editor editor;\nfn void f()\n{\n    editor.<caret>\n}\n");
		myFixture.completeBasic();
		List<String> lookups = myFixture.getLookupElementStrings();
		System.out.println("[TEST] lookups = " + lookups);
		assertNotNull("expected member completions", lookups);
		assertTrue("x missing: " + lookups, lookups.contains("x"));
		assertTrue("y missing: " + lookups, lookups.contains("y"));
	}

	public void testMemberCompletionOnGlobalFromImportedModule()
	{
		myFixture.addFileToProject("state.c3", "module state;\n" + EDITOR + "Editor editor;\n");
		myFixture.configureByText("app.c3", "module app;\nimport state;\nfn void f()\n{\n    editor.<caret>\n}\n");
		myFixture.completeBasic();
		List<String> lookups = myFixture.getLookupElementStrings();
		System.out.println("[TEST] lookups = " + lookups);
		assertTrue("x missing: " + lookups, lookups != null && lookups.contains("x"));
	}

	public void testUnusedGlobalIsStillReported()
	{
		myFixture.enableInspections(new C3UnusedDeclarationInspection());
		myFixture.configureByText("app.c3", "module app;\nint used;\nint unused;\nfn void f()\n{\n    used = 1;\n}\n");
		List<String> unused = myFixture.doHighlighting().stream()
			.map(HighlightInfo::getDescription)
			.filter(d -> d != null && d.contains("is never used"))
			.toList();
		System.out.println("[TEST] unused = " + unused);
		assertTrue(unused.toString(), unused.contains("Global 'unused' is never used"));
		assertFalse(unused.toString(), unused.contains("Global 'used' is never used"));
	}
}
