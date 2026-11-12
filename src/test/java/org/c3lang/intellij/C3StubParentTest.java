package org.c3lang.intellij;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.index.NameIndex;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3ModuleDefinition;
import org.c3lang.intellij.psi.C3Parameter;
import org.c3lang.intellij.psi.C3PsiElement;
import org.c3lang.intellij.psi.impl.C3StubBasedPsiElementBase;

import java.util.Collection;

/**
 * Elements of a file loaded from the index, whether its stub tree or its AST came first, find
 * their module section. The module section is not a stub element, so a parent walk through the
 * stub tree skips it; getModuleDefinition() walks the AST instead.
 */
public class C3StubParentTest extends C3TestCase
{
	private static final String SOURCE =
		"module net::sock;\nstruct Socket { int fd; }\nfn int Socket.read_some(&self, char[] buffer) { return 0; }\n";

	private C3FuncDef indexedFunction()
	{
		Collection<C3PsiElement> found = StubIndex.getElements(
			NameIndex.KEY, "net::sock::Socket.read_some", getProject(), GlobalSearchScope.allScope(getProject()), C3PsiElement.class);
		if (found.isEmpty())
		{
			found = StubIndex.getElements(
				NameIndex.KEY, "net::sock::read_some", getProject(), GlobalSearchScope.allScope(getProject()), C3PsiElement.class);
		}
		assertEquals(1, found.size());
		return (C3FuncDef) found.iterator().next();
	}

	private static void assertModuleFound(C3FuncDef funcDef, String state)
	{
		C3Parameter self = PsiTreeUtil.findChildOfType(funcDef.getFnParameterList(), C3Parameter.class);
		assertNotNull(self);
		System.out.println("[TEST] " + state + ": greenStub=" + (((C3StubBasedPsiElementBase<?>) funcDef).getGreenStub() != null)
			+ " funcDef.getParent()=" + funcDef.getParent().getClass().getSimpleName());
		C3ModuleDefinition module = self.findModuleDefinition();
		assertNotNull(state + ": the parameter has no module section", module);
		assertEquals("net::sock", module.getModuleName().getValue());
		assertSame(module, funcDef.getModuleDefinition());
		assertEquals("net::sock", self.getModuleDefinition().getModuleName().getValue());
	}

	/** Stub-backed PSI first (from the index), AST loaded afterwards. */
	public void testStubFirstThenAst()
	{
		myFixture.addFileToProject("sock.c3", SOURCE);
		PsiManagerEx.getInstanceEx(getProject()).dropPsiCaches();
		C3FuncDef funcDef = indexedFunction();
		assertNotNull("expected stub mode", ((PsiFileImpl) funcDef.getContainingFile()).getStubTree());
		assertModuleFound(funcDef, "stub first");
	}

	/** AST first (as a search walking the tree does), then the index adds a stub tree on the side. */
	public void testAstFirstThenStubTree()
	{
		VirtualFile vFile = myFixture.addFileToProject("sock.c3", SOURCE).getVirtualFile();
		PsiManagerEx.getInstanceEx(getProject()).dropPsiCaches();
		PsiFile file = PsiManager.getInstance(getProject()).findFile(vFile);
		assertNotNull(file);
		assertNotNull(file.getNode()); // AST loaded, elements created from it
		C3FuncDef funcDef = indexedFunction();
		assertModuleFound(funcDef, "ast first");
	}
}
