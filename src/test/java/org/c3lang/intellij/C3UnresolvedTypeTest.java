package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.c3lang.intellij.intention.C3UnresolvedTypeInspection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class C3UnresolvedTypeTest extends BasePlatformTestCase
{
	private static final String OBJECT = "module stunk::object;\nstruct Object { int x; }\n";

	private long unresolvedTypeErrors(@NotNull String settings)
	{
		myFixture.enableInspections(new C3UnresolvedTypeInspection());
		myFixture.addFileToProject("object.c3", OBJECT);
		myFixture.configureByText("settings.c3", settings);
		List<HighlightInfo> infos = myFixture.doHighlighting(HighlightSeverity.ERROR);
		System.out.println("[TEST] errors = " + infos.stream().map(HighlightInfo::getDescription).toList());
		return infos.stream().filter(i -> i.getDescription() != null
			&& i.getDescription().contains("Unresolved type")).count();
	}

	public void testUnimportedTypeIsError()
	{
		assertEquals(1, unresolvedTypeErrors(
			"module stunk::settings;\nfn void foo(Object* data) {}\n"));
	}

	public void testImportedTypeIsNotError()
	{
		assertEquals(0, unresolvedTypeErrors(
			"module stunk::settings;\nimport stunk::object;\nfn void foo(Object* data) {}\n"));
	}

	public void testSameModuleTypeIsNotError()
	{
		assertEquals(0, unresolvedTypeErrors(
			"module stunk::object;\nstruct Other { int y; }\nfn void foo(Object* data) {}\n"));
	}

	public void testPrimitiveTypeIsNotError()
	{
		assertEquals(0, unresolvedTypeErrors(
			"module stunk::settings;\nfn void foo(int* data) {}\n"));
	}

	/** Probe: a name with no type declaration anywhere is left alone (no import to offer). */
	public void testUnknownTypeIsNotFlagged()
	{
		assertEquals(0, unresolvedTypeErrors(
			"module stunk::settings;\nfn void foo(Nonexistent* data) {}\n"));
	}

	public void testImportQuickFixAddsImport()
	{
		myFixture.enableInspections(new C3UnresolvedTypeInspection());
		myFixture.addFileToProject("object.c3", OBJECT);
		myFixture.configureByText("settings.c3",
			"module stunk::settings;\nfn void foo(Obj<caret>ect* data) {}\n");
		myFixture.doHighlighting();

		IntentionAction fix = myFixture.findSingleIntention("Import stunk::object");
		assertNotNull("expected an 'Import stunk::object' quick fix", fix);
		myFixture.launchAction(fix);

		String text = myFixture.getEditor().getDocument().getText();
		System.out.println("[TEST] document after fix:\n" + text);
		assertTrue("quick fix should add the import", text.contains("import stunk::object;"));
		assertEquals("error should be gone after importing", 0, myFixture.doHighlighting(HighlightSeverity.ERROR)
			.stream().filter(i -> i.getDescription() != null && i.getDescription().contains("Unresolved type"))
			.count());
	}
}
