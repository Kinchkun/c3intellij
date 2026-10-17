package org.c3lang.intellij.testing;

import com.intellij.psi.PsiElement;
import org.c3lang.intellij.C3TestCase;

public class C3TestLocatorTest extends C3TestCase
{
	public void testFindsTestFunctionByFullName()
	{
		myFixture.addFileToProject("lib/argparse.c3l/test/argparse_test.c3",
			"module argparse_test @test;\n\nfn void can_parse_rest() @test {}\nfn void other() {}\n");
		myFixture.addFileToProject("other.c3", "module other_test;\n\nfn void can_parse_rest() {}\n");

		PsiElement test = C3TestLocator.findTest(getProject(), "argparse_test::can_parse_rest");
		assertNotNull(test);
		assertEquals("argparse_test.c3", test.getContainingFile().getName());
		assertNull(C3TestLocator.findTest(getProject(), "argparse_test::missing"));
	}
}
