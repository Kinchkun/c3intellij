package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.intention.C3UnusedDeclarationInspection;
import org.c3lang.intellij.psi.C3BaseType;
import org.c3lang.intellij.psi.C3TypeName;

import java.util.List;

/** A generic struct used only through instantiations (`Container { String }`) is not unused. */
public class C3UnusedGenericTest extends C3TestCase
{
	private static final String CONTAINER = """
		module app;
		struct Container <Type> {
		    Type* destination;
		    String name;
		}
		""";

	private List<String> unused(String text)
	{
		myFixture.enableInspections(new C3UnusedDeclarationInspection());
		myFixture.configureByText("a.c3", text);
		return myFixture.doHighlighting().stream()
			.map(HighlightInfo::getDescription)
			.filter(d -> d != null && d.contains("is never used"))
			.toList();
	}

	public void testGenericStructUsedOnlyInAliasIsNotUnused()
	{
		List<String> unused = unused(CONTAINER + """
			alias Rest       = Container { String[] };
			alias Positional = Container { String };
			fn void use(Rest* r, Positional* p) {}
			""");
		assertFalse(unused.toString(), unused.contains("Struct 'Container' is never used"));
	}

	public void testUnusedGenericStructIsStillReported()
	{
		assertTrue(unused(CONTAINER).contains("Struct 'Container' is never used"));
	}

	public void testInstantiationResolvesToTheGenericStruct()
	{
		myFixture.configureByText("a.c3", CONTAINER + "alias Rest = Container { String[] };\n");
		C3BaseType instantiation = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), C3BaseType.class).stream()
			.filter(t -> t.getText().startsWith("Container"))
			.findFirst().orElseThrow();

		assertEquals("Container", instantiation.getName());
		PsiElement target = instantiation.getReference().resolve();
		assertTrue(String.valueOf(target), target instanceof C3TypeName);
		assertEquals("Container", ((C3TypeName) target).getName());
	}

	public void testQualifiedInstantiationResolves()
	{
		myFixture.addFileToProject("lib.c3", "module mylib::box;\nstruct Box <Type> { Type value; }\n");
		myFixture.configureByText("a.c3", "module app;\nimport mylib::box;\nalias IntBox = box::Box { int };\n");
		C3BaseType instantiation = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), C3BaseType.class).stream()
			.filter(t -> t.getText().startsWith("box::Box"))
			.findFirst().orElseThrow();

		PsiElement target = instantiation.getReference().resolve();
		assertTrue(String.valueOf(target), target instanceof C3TypeName);
	}
}
