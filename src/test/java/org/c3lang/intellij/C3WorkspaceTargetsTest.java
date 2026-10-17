package org.c3lang.intellij;

import org.c3lang.intellij.wizard.C3ProjectTemplates;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/** Reading and adding project.json targets, and scaffolding apps/libraries of a workspace. */
public class C3WorkspaceTargetsTest
{
	private static final String WORKSPACE = """
		{
		  "dependency-search-paths": [ "lib" ],
		  "dependencies": [ ],
		  "test-sources": [ "test/**" ],
		  "targets": {
		    "tool": { "type": "executable", "sources": [ "apps/tool/src/**" ], "test-sources": [ "apps/tool/test/**" ] },
		    "core": { "type": "static-lib", "sources": [ "core/**" ] }
		  },
		  "opt": "O0"
		}
		""";

	private static Path workspace() throws IOException
	{
		Path dir = Files.createTempDirectory("c3-workspace");
		Files.writeString(dir.resolve("project.json"), WORKSPACE);
		return dir;
	}

	@Test
	public void readsTargetsInOrderWithInheritedTestSources() throws Exception
	{
		List<C3ProjectManifest.Target> targets = C3ProjectManifest.targets(workspace().toString());

		assertEquals(2, targets.size());
		assertEquals(new C3ProjectManifest.Target("tool", "executable", List.of("apps/tool/src/**"), List.of("apps/tool/test/**")),
			targets.get(0));
		assertTrue(targets.get(0).isExecutable());
		assertEquals("static-lib", targets.get(1).type());
		assertEquals(List.of("test/**"), targets.get(1).testSources());
	}

	@Test
	public void createAppTargetAddsTargetAndFiles() throws Exception
	{
		Path dir = workspace();
		String main = C3ProjectTemplates.createAppTarget(dir, "git-check");

		assertEquals("apps/git-check/src/main.c3", main);
		assertTrue(Files.readString(dir.resolve(main)).startsWith("module git_check;"));
		assertTrue(Files.exists(dir.resolve("apps/git-check/test/git_check_test.c3")));

		C3ProjectManifest.Target added = C3ProjectManifest.targets(dir.toString()).get(2);
		assertEquals(new C3ProjectManifest.Target("git-check", "executable",
			List.of("apps/git-check/src/**"), List.of("apps/git-check/test/**")), added);
		// Unrelated settings survive the rewrite.
		assertTrue(Files.readString(dir.resolve("project.json")).contains("\"opt\": \"O0\""));
		assertEquals("git-check", C3ProjectManifest.findTargetForFile(dir.toString(), "apps/git-check/test/git_check_test.c3", null, true));
	}

	@Test(expected = IOException.class)
	public void createAppTargetRejectsDuplicates() throws Exception
	{
		C3ProjectTemplates.createAppTarget(workspace(), "tool");
	}

	@Test
	public void createWorkspaceLibraryAddsDependencyAndFiles() throws Exception
	{
		Path dir = workspace();
		String source = C3ProjectTemplates.createWorkspaceLibrary(dir, "argparse");

		assertEquals("lib/argparse.c3l/src/argparse.c3", source);
		assertTrue(Files.readString(dir.resolve("lib/argparse.c3l/manifest.json")).contains("\"provides\": \"argparse\""));
		assertTrue(Files.exists(dir.resolve("lib/argparse.c3l/test/argparse_test.c3")));
		assertEquals(List.of(dir.resolve("lib/argparse.c3l")), C3ProjectManifest.dependencyLibraries(dir.toString()));
	}

	@Test
	public void dependencySearchPathsDefaultToLib() throws Exception
	{
		Path dir = Files.createTempDirectory("c3-workspace");
		Files.writeString(dir.resolve("project.json"), "{ \"targets\": {} }");
		assertEquals(List.of("lib"), C3ProjectManifest.dependencySearchPaths(dir.toString()));
	}
}
