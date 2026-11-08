package org.c3lang.intellij;

import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.usages.Usage;

import java.util.Arrays;
import java.util.Collection;

/**
 * Go to Implementation on a usage navigates to the declaration, like Go to Declaration. The
 * platform includes the resolved element only when the caret is not on a named element, and the
 * plugin's usage elements are named, so without {@code C3TargetElementEvaluator} the IDE
 * reported "No implementations found".
 */
public class C3GotoImplementationTest extends C3TestCase
{
	private PsiElement[] implementations()
	{
		GotoTargetHandler.GotoData data = CodeInsightTestUtil.gotoImplementation(myFixture.getEditor(), myFixture.getFile());
		System.out.println("[TEST] source=" + (data == null ? null : data.source) + " targets=" + (data == null ? null : Arrays.toString(data.targets)));
		assertNotNull("no source element at the caret", data);
		return data.targets;
	}

	private static void assertSingleTarget(PsiElement[] targets, String text)
	{
		assertEquals("expected one implementation, got " + Arrays.toString(targets), 1, targets.length);
		assertTrue(targets[0].getText(), targets[0].getText().contains(text));
	}

	public void testQualifiedTypeUsage()
	{
		myFixture.addFileToProject("sdl.c3", "module sdl;\ntypedef Window = void;\n");
		myFixture.addFileToProject("frontend.c3", "module ted::frontend;\nstruct Window { int w; }\n");
		myFixture.configureByText("main.c3", "module ted;\nimport sdl, ted::frontend;\nfrontend::Win<caret>dow window;\n");
		PsiElement[] targets = implementations();
		assertSingleTarget(targets, "Window");
		assertEquals("frontend.c3", targets[0].getContainingFile().getName());
	}

	public void testUnqualifiedTypeUsage()
	{
		myFixture.configureByText("main.c3", "module app;\nstruct Editor { int x; }\nfn void f()\n{\n    Edi<caret>tor e;\n}\n");
		assertSingleTarget(implementations(), "Editor");
	}

	public void testMethodCall()
	{
		myFixture.configureByText("main.c3",
			"module app;\nstruct Editor { int x; }\nfn void Editor.move_right(&self) {}\nfn void f()\n{\n    Editor e;\n    e.move_r<caret>ight();\n}\n");
		assertSingleTarget(implementations(), "move_right");
	}

	public void testGlobalUsage()
	{
		myFixture.configureByText("main.c3", "module app;\nint counter;\nfn void f()\n{\n    int v = coun<caret>ter;\n}\n");
		assertSingleTarget(implementations(), "counter");
	}

	/** The Find Usages action from a usage, with a same-named type elsewhere, finds the usages of the referenced struct. */
	public void testFindUsagesActionFromQualifiedUsage()
	{
		myFixture.addFileToProject("sdl.c3", "module sdl;\ntypedef Window = void;\n");
		myFixture.addFileToProject("frontend.c3", "module ted::frontend;\nstruct Window { int w; }\n");
		myFixture.configureByText("main.c3",
			"module ted;\nimport sdl, ted::frontend;\nfrontend::Win<caret>dow window;\nfn void f(frontend::Window w) {}\n");
		Collection<Usage> usages = myFixture.testFindUsagesUsingAction("main.c3");
		assertEquals(usages.toString(), 2, usages.size());
	}
}
