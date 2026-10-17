package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import org.c3lang.intellij.intention.C3UnresolvedMethodInspection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tests for {@link C3UnresolvedMethodInspection}. Each "must not flag" case guards against a specific
 * false-positive class — add one whenever you find valid code being marked red.
 */
public class C3UnresolvedMethodTest extends C3TestCase
{
	/** A struct with a field and a method, used as the receiver in most cases. */
	private static final String BASE =
		"module app;\n"
			+ "struct HttpClient { int fd; }\n"
			+ "fn void HttpClient.set_opt(&self) {}\n";

	private long unresolvedErrors(@NotNull String src)
	{
		myFixture.enableInspections(new C3UnresolvedMethodInspection());
		myFixture.configureByText("app.c3", src);
		List<HighlightInfo> infos = myFixture.doHighlighting(HighlightSeverity.ERROR);
		System.out.println("[TEST] errors = " + infos.stream().map(HighlightInfo::getDescription).toList());
		return infos.stream()
			.filter(i -> i.getDescription() != null && i.getDescription().contains("Cannot resolve"))
			.count();
	}

	// ---- positive: a typo'd method/field on a known struct IS an error ----

	public void testMissingMethodIsError()
	{
		assertEquals(1, unresolvedErrors(BASE
			+ "fn void run() {\n  HttpClient c;\n  c.this_method_does_not_exist();\n}\n"));
	}

	public void testMissingFieldIsError()
	{
		assertEquals(1, unresolvedErrors(BASE
			+ "fn void run() {\n  HttpClient c;\n  c.nope;\n}\n"));
	}

	// ---- negatives: valid code must NOT be flagged ----

	public void testExistingMethodIsNotError()
	{
		assertEquals(0, unresolvedErrors(BASE
			+ "fn void run() {\n  HttpClient c;\n  c.set_opt();\n}\n"));
	}

	public void testExistingFieldIsNotError()
	{
		assertEquals(0, unresolvedErrors(BASE
			+ "fn void run() {\n  HttpClient c;\n  c.fd;\n}\n"));
	}

	/** Receiver type unknown → cannot conclude the method is missing → must not flag. */
	public void testUnknownReceiverTypeIsNotFlagged()
	{
		assertEquals(0, unresolvedErrors(
			"module app;\nfn void run(Unknown* x) {\n  x.whatever();\n}\n"));
	}

	/** A non-struct (built-in) receiver: its built-in members must not be flagged. */
	public void testSliceBuiltinMemberIsNotFlagged()
	{
		assertEquals(0, unresolvedErrors(
			"module app;\nfn void run() {\n  int[] s;\n  usz n = s.len;\n}\n"));
	}

	/** Only the root cause is reported in a broken chain, not every following access. */
	public void testBrokenChainReportsOnce()
	{
		assertEquals(1, unresolvedErrors(BASE
			+ "fn void run() {\n  HttpClient c;\n  c.bad.good();\n}\n"));
	}

	// ---- cross-module: the reported real shape (pointer + initializer + imported struct) ----

	/** A struct in another module, with a field and a method, plus a factory returning a pointer. */
	private static final String LIB =
		"module lib;\n"
			+ "struct Object { int x; }\n"
			+ "fn void Object.greet(&self) {}\n"
			+ "fn Object* make() { return null; }\n";

	private long crossModuleErrors(@NotNull String appBody)
	{
		myFixture.enableInspections(new C3UnresolvedMethodInspection());
		myFixture.addFileToProject("lib.c3", LIB);
		myFixture.configureByText("app.c3",
			"module app;\nimport lib;\nfn void run() {\n  Object* object = make();\n" + appBody + "}\n");
		return myFixture.doHighlighting(HighlightSeverity.ERROR).stream()
			.filter(i -> i.getDescription() != null && i.getDescription().contains("Cannot resolve")).count();
	}

	/** The reported case: a missing method on an imported, pointer-typed receiver IS an error. */
	public void testCrossModuleMissingMethodIsError()
	{
		assertEquals(1, crossModuleErrors("  object.this_method_does_not_exists();\n"));
	}

	/** A method that exists in the other module must NOT be flagged. */
	public void testCrossModuleExistingMethodIsNotError()
	{
		assertEquals(0, crossModuleErrors("  object.greet();\n"));
	}

	/** A field that exists in the other module must NOT be flagged (guards cross-module field resolution). */
	public void testCrossModuleExistingFieldIsNotError()
	{
		assertEquals(0, crossModuleErrors("  int n = object.x;\n"));
	}
}
