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

	/** A name with no type declaration anywhere is flagged as unresolved (no import fix to offer). */
	public void testUnknownTypeIsFlagged()
	{
		assertEquals(1, unresolvedTypeErrors(
			"module stunk::settings;\nfn void foo(Nonexistent* data) {}\n"));
	}

	/** A module-level generic parameter resolves to no declaration but is not an error. */
	public void testModuleGenericParameterIsNotFlagged()
	{
		assertEquals(0, unresolvedTypeErrors(
			"module stunk::gen <Type>;\nfn void foo(Type* data) {}\n"));
	}

	/** A generic parameter declared on the enclosing declaration is not an error. */
	public void testDeclarationGenericParameterIsNotFlagged()
	{
		assertEquals(0, unresolvedTypeErrors(
			"module stunk::settings;\nstruct Wrap <Type> { Type value; }\n"));
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

	/**
	 * {@code struct ByteWriter (OutStream)} in other modules (std::core::dstring, std::io, …) must not
	 * count as declarations of {@code OutStream}, or an un-imported {@code OutStream} looks visible.
	 */
	public void testUnimportedInterfaceIsErrorDespiteImplementations()
	{
		myFixture.addFileToProject("io.c3", "module std::io;\ninterface OutStream\n{\n    fn usz? write(char[] bytes);\n}\n");
		myFixture.addFileToProject("dstring.c3", "module std::core::dstring;\nimport std::io;\n"
			+ "typedef DString (OutStream) = void*;\nstruct Writer (OutStream) { int x; }\n");
		assertEquals(1, unresolvedTypeErrors(
			"module argparse;\nstruct ArgParse { int x; }\nfn void? ArgParse.write_usage(&self, OutStream out) {}\n"));
	}

	public void testUnimportedInterfaceInImplementsListIsError()
	{
		myFixture.addFileToProject("io.c3", "module std::io;\ninterface OutStream\n{\n    fn usz? write(char[] bytes);\n}\n");
		assertEquals(1, unresolvedTypeErrors("module app;\nstruct Writer (OutStream) { int x; }\n"));
	}

	public void testInterfaceImportQuickFixAddsImport()
	{
		myFixture.enableInspections(new C3UnresolvedTypeInspection());
		myFixture.addFileToProject("io.c3", "module std::io;\ninterface OutStream\n{\n    fn usz? write(char[] bytes);\n}\n");
		myFixture.configureByText("argparse.c3",
			"module argparse;\nstruct ArgParse { int x; }\nfn void? ArgParse.write_usage(&self, Out<caret>Stream out) {}\n");
		myFixture.doHighlighting();
		myFixture.launchAction(myFixture.findSingleIntention("Import std::io"));
		assertTrue(myFixture.getEditor().getDocument().getText().contains("import std::io;"));
	}
}
