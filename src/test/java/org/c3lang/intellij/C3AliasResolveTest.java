package org.c3lang.intellij;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.c3lang.intellij.psi.C3AliasName;

import java.util.List;

/**
 * An alias ({@code alias mem @builtin = thread_allocator;}) resolves from its usages, also from
 * the auto-imported {@code std::core} modules and from imported modules; Find Usages and member
 * access through it work; a constant alias resolves too.
 */
public class C3AliasResolveTest extends C3TestCase
{
	private static final String ALLOCATORS =
		"module std::core::mem::allocators;\n"
			+ "struct Allocator { int kind; }\n"
			+ "Allocator thread_allocator;\n"
			+ "alias mem @builtin = thread_allocator;\n";

	private PsiElement resolveAtCaret()
	{
		PsiReference reference = myFixture.getReferenceAtCaretPosition();
		assertNotNull("expected a reference at the caret", reference);
		PsiElement resolved = reference.resolve();
		System.out.println("[TEST] resolved to " + resolved);
		return resolved;
	}

	private static void assertAlias(PsiElement resolved, String name)
	{
		assertTrue("expected the alias '" + name + "', got " + resolved, resolved instanceof C3AliasName);
		assertEquals(name, ((C3AliasName) resolved).getName());
	}

	public void testStdCoreAliasResolvesWithoutImport()
	{
		myFixture.addFileToProject("allocators.c3", ALLOCATORS);
		myFixture.configureByText("app.c3",
			"module app;\nfn void use(Allocator a) {}\nfn void f()\n{\n    use(m<caret>em);\n}\n");
		assertAlias(resolveAtCaret(), "mem");
	}

	public void testAliasFromImportedModuleResolves()
	{
		myFixture.addFileToProject("lib.c3", "module lib;\nint counter;\nalias count = counter;\n");
		myFixture.configureByText("app.c3",
			"module app;\nimport lib;\nfn void f()\n{\n    int v = cou<caret>nt;\n}\n");
		assertAlias(resolveAtCaret(), "count");
	}

	public void testAliasNotVisibleWithoutImport()
	{
		myFixture.addFileToProject("lib.c3", "module lib;\nint counter;\nalias count = counter;\n");
		myFixture.configureByText("app.c3",
			"module app;\nfn void f()\n{\n    int v = cou<caret>nt;\n}\n");
		assertNull("an alias of a module that is not imported must not resolve", resolveAtCaret());
	}

	public void testConstantAliasResolves()
	{
		myFixture.configureByText("app.c3",
			"module app;\nconst int LIMIT = 3;\nalias MAX = LIMIT;\nfn void f()\n{\n    int v = M<caret>AX;\n}\n");
		assertAlias(resolveAtCaret(), "MAX");
	}

	public void testFindUsagesFromAliasDeclaration()
	{
		myFixture.addFileToProject("allocators.c3", ALLOCATORS);
		myFixture.configureByText("app.c3",
			"module app;\nfn void use(Allocator a) {}\nfn void f()\n{\n    use(mem);\n    use(mem);\n}\n");
		PsiElement[] aliases = new PsiElement[1];
		myFixture.openFileInEditor(myFixture.findFileInTempDir("allocators.c3"));
		myFixture.getEditor().getCaretModel().moveToOffset(ALLOCATORS.indexOf("mem @builtin") + 1);
		PsiElement target = myFixture.getElementAtCaret();
		assertAlias(target, "mem");
		assertEquals(2, myFixture.findUsages(target).size());
	}

	public void testMemberCompletionThroughAlias()
	{
		myFixture.addFileToProject("allocators.c3", ALLOCATORS);
		myFixture.configureByText("app.c3", "module app;\nfn void f()\n{\n    mem.<caret>\n}\n");
		myFixture.completeBasic();
		List<String> lookups = myFixture.getLookupElementStrings();
		System.out.println("[TEST] lookups = " + lookups);
		assertTrue("kind missing: " + lookups, lookups != null && lookups.contains("kind"));
	}
}
