package org.c3lang.intellij;

import com.intellij.codeInsight.hint.ImplementationTextSelectioner;
import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.c3lang.intellij.psi.C3AliasName;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3GlobalName;
import org.c3lang.intellij.psi.C3TypeName;

/**
 * Quick Definition shows a whole declaration: the function with its body, the struct or enum with
 * its members, the global or alias line, not only the named element a usage resolves to.
 */
public class C3QuickDefinitionTest extends C3TestCase
{
	private static final String METHOD = "fn void Editor.move_right(&self)\n{\n    self.x++;\n    self.x++;\n}";
	private static final String FUNCTION = "fn int twice(int v)\n{\n    return 2 * v;\n}";
	private static final String STRUCT = "struct Window {\n    int width;\n    int height;\n}";
	private static final String ENUM = "enum Mode : int {\n    INSERT,\n    NORMAL,\n}";

	private String shownText()
	{
		return shownText(C3FuncDef.class);
	}

	private String shownText(Class<? extends PsiElement> resolvedKind)
	{
		PsiReference reference = myFixture.getReferenceAtCaretPosition();
		assertNotNull(reference);
		PsiElement resolved = reference.resolve();
		assertTrue("expected a " + resolvedKind.getSimpleName() + ", got " + resolved, resolvedKind.isInstance(resolved));
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

	public void testTypeUsageShowsWholeStruct()
	{
		myFixture.addFileToProject("frontend.c3", "module ted::frontend;\n" + STRUCT + "\n");
		myFixture.configureByText("main.c3", "module ted;\nimport ted::frontend;\nfrontend::Win<caret>dow window;\n");
		assertEquals(STRUCT, shownText(C3TypeName.class));
	}

	public void testTypeUsageShowsWholeEnum()
	{
		myFixture.configureByText("app.c3", "module app;\n" + ENUM + "\nfn void f()\n{\n    Mo<caret>de m = Mode.INSERT;\n}\n");
		assertEquals(ENUM, shownText(C3TypeName.class));
	}

	public void testGlobalUsageShowsDeclaration()
	{
		myFixture.configureByText("app.c3", "module app;\nint counter = 1;\nfn void f()\n{\n    int v = coun<caret>ter;\n}\n");
		assertEquals("int counter = 1;", shownText(C3GlobalName.class));
	}

	public void testAliasUsageShowsDeclaration()
	{
		myFixture.configureByText("app.c3", "module app;\nint counter;\nalias count @builtin = counter;\nfn void f()\n{\n    int v = cou<caret>nt;\n}\n");
		assertEquals("alias count @builtin = counter;", shownText(C3AliasName.class));
	}

	public void testFunctionCallShowsWholeFunction()
	{
		myFixture.configureByText("app.c3",
			"module app;\n" + FUNCTION + "\nfn void f()\n{\n    int v = twi<caret>ce(1);\n}\n");
		assertEquals(FUNCTION, shownText());
	}
}
