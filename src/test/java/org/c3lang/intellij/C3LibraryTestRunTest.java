package org.c3lang.intellij;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * Tests inside a directory library ({@code lib/<name>.c3l/test}) are not part of any project target,
 * so the gutter runs them through {@code c3c compile-test --libdir ... --lib ...}.
 */
public class C3LibraryTestRunTest extends C3TestCase
{
	private static final String MANIFEST = "{ \"provides\": \"argparse\", \"sources\": [ \"src/**\" ] }";

	public void testFindsEnclosingLibrary()
	{
		myFixture.addFileToProject("lib/argparse.c3l/manifest.json", MANIFEST);
		VirtualFile test = myFixture.addFileToProject("lib/argparse.c3l/test/a_test.c3", "module a_test @test;").getVirtualFile();
		VirtualFile app = myFixture.addFileToProject("apps/tool/src/main.c3", "module tool;").getVirtualFile();

		assertEquals("argparse.c3l", C3Projects.findLibraryDir(test).getName());
		assertNull(C3Projects.findLibraryDir(app));
	}

	public void testLibraryInsideProjectRunsFromProjectDir()
	{
		myFixture.addFileToProject("project.json", "{ \"targets\": {} }");
		myFixture.addFileToProject("lib/argparse.c3l/manifest.json", MANIFEST);
		myFixture.addFileToProject("lib/argparse.c3l/src/argparse.c3", "module argparse;");
		myFixture.addFileToProject("lib/argparse.c3l/test/b_test.c3", "module b_test @test;");
		VirtualFile test = myFixture.addFileToProject("lib/argparse.c3l/test/a_test.c3", "module a_test @test;").getVirtualFile();

		String args = C3TargetRunner.libraryTestArgs(C3Projects.findLibraryDir(test), "a_test::");
		assertEquals("--libdir lib --lib argparse lib/argparse.c3l/test/a_test.c3 lib/argparse.c3l/test/b_test.c3"
			+ " --build-dir build -o build/argparse_test --test-filter a_test::", args);
	}

	public void testLibraryWithoutTestsFallsBack()
	{
		myFixture.addFileToProject("lib/argparse.c3l/manifest.json", MANIFEST);
		VirtualFile src = myFixture.addFileToProject("lib/argparse.c3l/src/argparse.c3", "module argparse;").getVirtualFile();

		assertNull(C3TargetRunner.libraryTestArgs(C3Projects.findLibraryDir(src), "argparse::"));
	}
}
