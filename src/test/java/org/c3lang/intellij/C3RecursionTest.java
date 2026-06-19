package org.c3lang.intellij;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.c3lang.intellij.psi.C3PathIdent;

/**
 * Guards against the StackOverflowError where resolving an unresolved member-access receiver
 * recursed: getReference -> isStructMemberAccess -> getRootType -> getReference -> ...
 */
public class C3RecursionTest extends BasePlatformTestCase
{
	public void testUnresolvedReceiverDoesNotOverflow()
	{
		// `unknownvar` resolves to nothing yet is the first path-ident inside a binary expression —
		// the shape getRootType() walks into, which used to recurse infinitely. Highlighting runs the
		// reference resolution that triggered it.
		myFixture.configureByText("a.c3",
			"module app;\n" +
			"fn void f()\n" +
			"{\n" +
			"    unknownvar + other;\n" +
			"}\n");

		myFixture.doHighlighting(); // must not throw StackOverflowError

		// Also resolve the receiver's reference directly — the exact recursion entry point.
		int offset = myFixture.getFile().getText().indexOf("unknownvar");
		C3PathIdent receiver = PsiTreeUtil.getParentOfType(
			myFixture.getFile().findElementAt(offset), C3PathIdent.class);
		assertNotNull("expected a path-ident at the receiver", receiver);
		PsiReference reference = receiver.getReference();
		PsiElement resolved = reference != null ? reference.resolve() : null; // must not overflow
		System.out.println("[TEST] receiver resolved to: " + resolved);
	}
}
