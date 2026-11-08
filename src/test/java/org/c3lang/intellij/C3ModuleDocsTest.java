package org.c3lang.intellij;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.c3lang.intellij.docs.C3DocumentationProvider;
import org.c3lang.intellij.psi.C3Module;

/** Quick Documentation on a module path shows the module's doc comment and never looks outside the file. */
public class C3ModuleDocsTest extends C3TestCase
{
	public void testModuleDocFromPath()
	{
		myFixture.addFileToProject("a.txt", "not a C3 file, a directory sibling of frontend.c3");
		myFixture.addFileToProject("frontend.c3", "<* A thin layer over SDL. *>\nmodule ted::frontend;\nstruct Window { int w; }\n");
		myFixture.configureByText("main.c3", "module ted;\nimport ted::frontend;\nfrontend::Window window;\n");
		int offset = myFixture.getFile().getText().indexOf("frontend::Window") + 1;
		PsiReference reference = myFixture.getFile().findReferenceAt(offset);
		assertNotNull(reference);
		PsiElement module = reference.resolve();
		assertTrue("expected the module declaration, got " + module, module instanceof C3Module);
		String html = new C3DocumentationProvider().generateDoc(module, null);
		assertNotNull(html);
		// The definition line is syntax-highlighted HTML; compare against its plain text.
		String doc = StringUtil.unescapeXmlEntities(StringUtil.removeHtmlTags(html));
		assertTrue(doc, doc.contains("module ted::frontend"));
		assertTrue(doc, doc.contains("A thin layer over SDL."));
	}
}
