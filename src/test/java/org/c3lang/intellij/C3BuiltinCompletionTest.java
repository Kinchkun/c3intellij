package org.c3lang.intellij;

import org.c3lang.intellij.psi.C3BuiltinPsiElement;
import org.c3lang.intellij.psi.C3FaultDefinition;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3MacroDefinition;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.List;

/**
 * A `@builtin` declaration from the auto-imported `std::core` modules completes to its bare name
 * (`abort(`), while an ordinary core function keeps its module path (`mem::malloc(`).
 */
public class C3BuiltinCompletionTest extends C3TestCase
{
	private static final String BUILTIN =
		"module std::core::builtin;\n"
			+ "macro void abort(String message) @builtin @noreturn { }\n"
			+ "fn void abandon(String message) { }\n"
			+ "faultdef NOT_FOUND @builtin, OTHER_FAULT;\n";
	private static final String MEM = "module std::core::mem;\nfn void* malloc(usz size) { return null; }\n";

	private String completeInBody(String body)
	{
		myFixture.addFileToProject("builtin.c3", BUILTIN);
		myFixture.addFileToProject("mem.c3", MEM);
		myFixture.configureByText("app.c3", "module app;\nfn void f()\n{\n" + body + "}\n");
		myFixture.completeBasic();
		List<String> lookups = myFixture.getLookupElementStrings();
		System.out.println("[TEST] lookups = " + lookups);
		if (lookups != null && !lookups.isEmpty())
		{
			myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[0]);
			myFixture.finishLookup('\n');
		}
		return myFixture.getEditor().getDocument().getText();
	}

	public void testBuiltinMacroCompletesWithoutModulePath()
	{
		String text = completeInBody("    abor<caret>\n");
		assertTrue(text, text.contains("    abort("));
		assertFalse(text, text.contains("builtin::"));
	}

	public void testOrdinaryCoreFunctionKeepsItsModulePath()
	{
		String text = completeInBody("    mallo<caret>\n");
		assertTrue(text, text.contains("    mem::malloc("));
	}

	public void testNonBuiltinFunctionInTheBuiltinModuleKeepsItsPath()
	{
		String text = completeInBody("    abando<caret>\n");
		assertTrue(text, text.contains("    builtin::abandon("));
	}

	public void testIsBuiltinReflectsTheAttribute()
	{
		myFixture.configureByText("builtin.c3", BUILTIN);
		C3MacroDefinition abort = PsiTreeUtil.findChildOfType(myFixture.getFile(), C3MacroDefinition.class);
		C3FuncDef abandon = PsiTreeUtil.findChildOfType(myFixture.getFile(), C3FuncDef.class);
		List<C3FaultDefinition> faults = List.copyOf(PsiTreeUtil.findChildrenOfType(myFixture.getFile(), C3FaultDefinition.class));
		assertTrue(((C3BuiltinPsiElement) abort).isBuiltin());
		assertFalse(((C3BuiltinPsiElement) abandon).isBuiltin());
		assertEquals(2, faults.size());
		assertTrue(((C3BuiltinPsiElement) faults.get(0)).isBuiltin());
		assertFalse(((C3BuiltinPsiElement) faults.get(1)).isBuiltin());
	}
}
