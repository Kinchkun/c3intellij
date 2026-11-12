package org.c3lang.intellij;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * Tests inside a directory library ({@code lib/<name>.c3l/test}) that no project target lists in its
 * test-sources are run by the gutter through {@code c3c compile-test --libdir ... --lib ...}; a test
 * that a target claims runs as that target.
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

	/**
	 * A library test that a project target lists in its test-sources runs as that target
	 * ({@code c3c test http_client}), which brings the target's dependencies along.
	 */
	public void testLibraryTestClaimedByProjectTargetRunsAsTarget() throws Exception
	{
		java.nio.file.Path workspace = java.nio.file.Files.createTempDirectory("c3-library-target");
		java.nio.file.Files.writeString(workspace.resolve("project.json"),
			"{ \"targets\": { \"http_client\": { \"type\": \"static-lib\","
				+ " \"sources\": [ \"lib/http_client.c3l/src/**\" ],"
				+ " \"test-sources\": [ \"lib/http_client.c3l/test/**\" ],"
				+ " \"dependencies\": [ \"curl\" ] } } }");
		java.nio.file.Path claimed = workspace.resolve("lib/http_client.c3l/test/server_test.c3");
		java.nio.file.Path unclaimed = workspace.resolve("lib/argparse.c3l/test/a_test.c3");
		for (java.nio.file.Path testFile : java.util.List.of(claimed, unclaimed))
		{
			java.nio.file.Files.createDirectories(testFile.getParent());
			java.nio.file.Files.writeString(testFile, "module some_test @test;");
		}

		var fileSystem = com.intellij.openapi.vfs.LocalFileSystem.getInstance();
		VirtualFile projectDir = fileSystem.refreshAndFindFileByNioFile(workspace);
		assertNotNull(projectDir);

		assertEquals("http_client",
			C3LineMarkerProvider.testTargetOf(projectDir, fileSystem.refreshAndFindFileByNioFile(claimed)));
		assertNull("no target claims the argparse tests, so they are compiled directly",
			C3LineMarkerProvider.testTargetOf(projectDir, fileSystem.refreshAndFindFileByNioFile(unclaimed)));
	}
}
