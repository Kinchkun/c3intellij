package org.c3lang.intellij;

import com.intellij.codeInsight.hint.ImplementationTextSelectioner;
import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.c3lang.intellij.psi.C3FuncDef;

/** Quick Definition on a call shows the whole function, body included, not only the resolved header. */
public class C3QuickDefinitionTest extends C3TestCase
{
	private static final String METHOD = "fn void Editor.move_right(&self)\n{\n    self.x++;\n    self.x++;\n}";
	private static final String FUNCTION = "fn int twice(int v)\n{\n    return 2 * v;\n}";

	private String shownText()
	{
		PsiReference reference = myFixture.getReferenceAtCaretPosition();
		assertNotNull(reference);
		PsiElement resolved = reference.resolve();
		assertTrue("expected the function header, got " + resolved, resolved instanceof C3FuncDef);
		ImplementationTextSelectioner selectioner =
			new LanguageExtension<ImplementationTextSelectioner>("com.intellij.lang.implementationTextSelectioner")
				.forLanguage(C3Language.INSTANCE);
		assertNotNull("no implementation text selectioner registered for C3", selectioner);
		String text = resolved.getContainingFile().getText();
		return text.substring(selectioner.getTextStartOffset(resolved), selectioner.getTextEndOffset(resolved));
	}

	public void testMethodCallShowsWholeMethod()
	{
		myFixture.configureByText("app.c3",
			"module app;\nstruct Editor { int x; }\n" + METHOD + "\nfn void f()\n{\n    Editor e;\n    e.move_r<caret>ight();\n}\n");
		assertEquals(METHOD, shownText());
	}

	public void testFunctionCallShowsWholeFunction()
	{
		myFixture.configureByText("app.c3",
			"module app;\n" + FUNCTION + "\nfn void f()\n{\n    int v = twi<caret>ce(1);\n}\n");
		assertEquals(FUNCTION, shownText());
	}
}
