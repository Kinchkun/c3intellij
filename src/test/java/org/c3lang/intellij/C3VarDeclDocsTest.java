package org.c3lang.intellij;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.docs.VarDeclDocs;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;

import java.util.List;

public class C3VarDeclDocsTest extends C3TestCase
{
	private List<C3LocalDeclAfterType> locals(String body)
	{
		myFixture.configureByText("a.c3", "module app;\n\nfn void f()\n{\n" + body + "}\n");
		return List.copyOf(
			PsiTreeUtil.collectElementsOfType(myFixture.getFile(), C3LocalDeclAfterType.class));
	}

	/** The definition line is syntax-highlighted HTML; compare against its plain text. */
	private static String doc(C3LocalDeclAfterType local)
	{
		return StringUtil.unescapeXmlEntities(StringUtil.removeHtmlTags(VarDeclDocs.generateVarDeclDoc(local)));
	}

	public void testStatementDeclarationShowsType()
	{
		C3LocalDeclAfterType local = locals("    int a = 1;\n").get(0);
		assertTrue(doc(local).contains("int a"));
	}

	public void testMultipleDeclaratorsShareTheType()
	{
		List<C3LocalDeclAfterType> locals = locals("    int a = 1, b = 2;\n");
		assertEquals(2, locals.size());
		assertTrue(doc(locals.get(1)).contains("int b"));
	}

	/** The type of a `for` initializer sits in decl_or_expr, not in a local_declaration_stmt. */
	public void testForLoopInitializerShowsType()
	{
		C3LocalDeclAfterType local = locals("    for (int i = 0; i < 3; i++) {}\n").get(0);
		assertTrue(doc(local).contains("int i"));
	}

	/** A declaration inside an `if` condition has the same decl_or_expr shape. */
	public void testConditionDeclarationShowsType()
	{
		C3LocalDeclAfterType local = locals("    if (int x = 1) {}\n").get(0);
		assertTrue(doc(local).contains("int x"));
	}
}
