package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import org.c3lang.intellij.intention.C3MissingGenericParametersInspection;

import java.util.List;

/** A generic type used without its type parameters is an error, except inside its own generic module. */
public class C3MissingGenericParametersTest extends C3TestCase
{
	private static final String LIST =
		"module std::collections::list <Type>;\nstruct List { usz size; Type[] entries; }\n"
			+ "fn void List.push(&self, Type t)\n{\n    List other;\n}\n";

	private List<String> errors(String fileName, String text)
	{
		myFixture.enableInspections(new C3MissingGenericParametersInspection());
		myFixture.configureByText(fileName, text);
		List<String> errors = myFixture.doHighlighting(HighlightSeverity.ERROR).stream()
			.map(HighlightInfo::getDescription)
			.filter(d -> d != null && d.contains("needs its type parameters"))
			.toList();
		System.out.println("[TEST] errors = " + errors);
		return errors;
	}

	public void testTypeOfGenericModuleWithoutParametersIsError()
	{
		myFixture.addFileToProject("list.c3", LIST);
		List<String> errors = errors("editor.c3",
			"module ted::editor;\nimport std::collections::list;\nstruct Editor {\n    List foo;\n    List { String } lines;\n    usz first_line;\n}\n");
		assertEquals(errors.toString(), 1, errors.size());
		assertTrue(errors.get(0), errors.get(0).contains("'List'"));
	}

	public void testGenericStructWithoutParametersIsError()
	{
		List<String> errors = errors("app.c3",
			"module app;\nstruct Container <Type> { Type* d; }\nContainer c;\nContainer { int } d;\n");
		assertEquals(errors.toString(), 1, errors.size());
	}

	public void testBareNameInsideTheGenericModuleIsFine()
	{
		assertEquals(0, errors("list.c3", LIST).size());
	}

	public void testNonGenericTypeIsFine()
	{
		myFixture.addFileToProject("list.c3", LIST);
		assertEquals(0, errors("app.c3", "module app;\nimport std::collections::list;\nstruct Cursor { int line; }\nstruct Editor {\n    Cursor cursor;\n    List { String } lines;\n}\n").size());
	}
}
