package org.c3lang.intellij;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.c3lang.intellij.psi.C3ConstdefConstant;
import org.c3lang.intellij.psi.C3EnumConstant;

/**
 * A {@code Type.CONSTANT} access navigates to the constant: enum constants and constdef/constset
 * constants, unqualified and through a module path ({@code sdl::Keycode.KEY_LEFT}).
 */
public class C3EnumAccessResolveTest extends C3TestCase
{
	private PsiElement gotoTarget()
	{
		PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
		assertNotNull("navigation must see a reference at the caret", reference);
		PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
		System.out.println("[TEST] targets = " + java.util.Arrays.toString(targets));
		assertEquals(1, targets.length);
		return targets[0];
	}

	public void testEnumConstantUnqualified()
	{
		myFixture.configureByText("main.c3",
			"module app;\nenum Keycode : uint { KEY_LEFT, KEY_RIGHT }\nfn void f(Keycode k)\n{\n    switch (k)\n    {\n        case Keycode.KEY_<caret>LEFT: return;\n        default: return;\n    }\n}\n");
		PsiElement target = gotoTarget();
		assertTrue(target.toString(), target instanceof C3EnumConstant);
		assertEquals("KEY_LEFT", ((C3EnumConstant) target).getName());
	}

	public void testEnumConstantQualified()
	{
		myFixture.addFileToProject("sdl.c3", "module sdl;\nenum Keycode : uint { KEY_LEFT, KEY_RIGHT }\n");
		myFixture.configureByText("main.c3",
			"module ted;\nimport sdl;\nfn void f(sdl::Keycode k)\n{\n    switch (k)\n    {\n        case sdl::Keycode.KEY_<caret>RIGHT: return;\n        default: return;\n    }\n}\n");
		PsiElement target = gotoTarget();
		assertTrue(target.toString(), target instanceof C3EnumConstant);
		assertEquals("KEY_RIGHT", ((C3EnumConstant) target).getName());
		assertEquals("sdl.c3", target.getContainingFile().getName());
	}

	public void testConstsetConstantQualified()
	{
		myFixture.addFileToProject("sdl.c3", "module sdl;\nconstset Keycode : uint\n{\n    KEY_EXTENDED_MASK = (1u << 29),\n    KEY_LEFT = 0x40000050u,\n}\n");
		myFixture.configureByText("main.c3",
			"module ted;\nimport sdl;\nfn void f(sdl::Keycode k)\n{\n    if (k == sdl::Keycode.KEY_<caret>LEFT) return;\n}\n");
		PsiElement target = gotoTarget();
		assertTrue(target.toString(), target instanceof C3ConstdefConstant);
		assertEquals("KEY_LEFT", ((C3ConstdefConstant) target).getName());
	}

	/** Find Usages of a constant still sees its `Type.CONSTANT` accesses through the standard search. */
	public void testFindUsagesOfConstant()
	{
		myFixture.addFileToProject("sdl.c3", "module sdl;\nenum Keycode : uint { KEY_LEFT, KEY_RIGHT }\n");
		myFixture.configureByText("main.c3",
			"module ted;\nimport sdl;\nfn void f(sdl::Keycode k)\n{\n    if (k == sdl::Keycode.KEY_<caret>LEFT) return;\n    if (k == sdl::Keycode.KEY_LEFT) return;\n}\n");
		PsiElement target = myFixture.getElementAtCaret();
		assertTrue(target.toString(), target instanceof C3EnumConstant);
		assertEquals(2, myFixture.findUsages(target).size());
	}
}
