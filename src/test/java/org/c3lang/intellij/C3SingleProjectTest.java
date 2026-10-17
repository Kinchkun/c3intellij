package org.c3lang.intellij;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/** The classic layout: one C3 project whose project.json sits at the IDE project root. */
public class C3SingleProjectTest extends C3TestCase
{
	public void testRootManifestOwnsEverything()
	{
		VirtualFile manifest = myFixture.addFileToProject("project.json", "{}").getVirtualFile();
		VirtualFile root = manifest.getParent();
		VirtualFile main = myFixture.addFileToProject("src/main.c3", "module app;").getVirtualFile();
		VirtualFile test = myFixture.addFileToProject("test/app_test.c3", "module app_test;").getVirtualFile();
		VirtualFile dep = myFixture.addFileToProject("lib/curl.c3l/curl.c3i", "module curl;").getVirtualFile();

		assertEquals(root, C3Projects.findProjectDir(main));
		assertEquals(root, C3Projects.findProjectDir(test));
		assertEquals(root, C3Projects.findProjectDir(dep));
		assertEquals(List.of(root), C3Projects.findAll(getProject()));
	}
}
