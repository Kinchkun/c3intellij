package org.c3lang.intellij;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3StructMemberDeclaration;

/** Local variables are visible in nested blocks and in the statement owning their header, not in sibling blocks. */
public class C3LocalResolveTest extends C3TestCase
{
	private PsiElement resolveAtCaret()
	{
		PsiReference reference = myFixture.getReferenceAtCaretPosition();
		assertNotNull(reference);
		PsiElement resolved = reference.resolve();
		System.out.println("[TEST] resolved to " + resolved + (resolved == null ? "" : " @" + resolved.getTextOffset()));
		return resolved;
	}

	private PsiElement resolveInBody(String body)
	{
		myFixture.configureByText("app.c3", "module app;\nfn void f()\n{\n" + body + "}\n");
		return resolveAtCaret();
	}

	private static void assertLocalAt(PsiElement resolved, String declarationText, String body)
	{
		assertTrue("expected a local declaration, got " + resolved, resolved instanceof C3LocalDeclAfterType);
		assertEquals(declarationText, resolved.getText());
	}

	public void testOuterBlockLocalIsVisibleInNestedBlocks()
	{
		PsiElement r = resolveInBody("    int x = 1;\n    while (true)\n    {\n        if (x > 0)\n        {\n            int y = <caret>x;\n        }\n    }\n");
		assertLocalAt(r, "x = 1", null);
	}

	public void testVisibleInsideTrailingBlockMacroCall()
	{
		myFixture.configureByText("app.c3",
			"module app;\nmacro void @zone(String name; @body)\n{\n    @body();\n}\n"
				+ "fn void f()\n{\n    int x = 1;\n    @zone(\"events\")\n    {\n        int y = <caret>x;\n    }\n}\n");
		assertLocalAt(resolveAtCaret(), "x = 1", null);
	}

	public void testSiblingBlockLocalIsNotVisible()
	{
		assertNull(resolveInBody("    if (true)\n    {\n        int x = 1;\n    }\n    int y = <caret>x;\n"));
	}

	public void testInnermostShadowingWins()
	{
		PsiElement r = resolveInBody("    int x = 1;\n    {\n        int x = 2;\n        int y = <caret>x;\n    }\n");
		assertLocalAt(r, "x = 2", null);
	}

	public void testLoopHeaderVariableIsVisibleInTheLoop()
	{
		PsiElement r = resolveInBody("    for (int i = 0; i < 3; i++)\n    {\n        int v = <caret>i;\n    }\n");
		assertLocalAt(r, "i = 0", null);
	}

	public void testLoopHeaderVariableIsNotVisibleAfterTheLoop()
	{
		assertNull(resolveInBody("    for (int i = 0; i < 3; i++)\n    {\n    }\n    int v = <caret>i;\n"));
	}

	/** The reported case: `switch (event.key.key)` nested in a loop and an `if`, with `sdl::Event event;` declared before the loop. */
	public void testMemberAccessThroughOuterLocalOfQualifiedType()
	{
		myFixture.addFileToProject("sdl.c3",
			"module sdl;\nstruct KeyboardEvent { uint type; uint key; }\nunion Event { uint type; KeyboardEvent key; }\nfn bool poll_event(Event* e) { return false; }\n");
		myFixture.configureByText("main.c3",
			"module ted;\nimport sdl;\nfn void f()\n{\n    sdl::Event event;\n    while (sdl::poll_event(&event))\n    {\n        if (event.type == 1)\n        {\n"
				+ "            switch (event.key.<caret>key)\n            {\n                default: return;\n            }\n        }\n    }\n}\n");
		PsiElement resolved = resolveAtCaret();
		assertTrue("expected the KeyboardEvent.key member, got " + resolved, resolved instanceof C3StructMemberDeclaration);
		assertEquals("uint key;", resolved.getText());
	}
}
