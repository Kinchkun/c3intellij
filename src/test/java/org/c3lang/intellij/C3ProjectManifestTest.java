package org.c3lang.intellij;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Verifies project.json parsing used by run/debug (output directory + executable targets). */
public class C3ProjectManifestTest
{
	private static Path writeManifest(String json) throws Exception
	{
		Path dir = Files.createTempDirectory("c3-manifest");
		Files.writeString(dir.resolve("project.json"), json);
		return dir;
	}

	@Test
	public void outputDirectoryReadsTheField() throws Exception
	{
		Path dir = writeManifest("{ \"output\": \"out/bin\", \"targets\": {} }");
		assertEquals("out/bin", C3ProjectManifest.outputDirectory(dir.toString()));
	}

	@Test
	public void outputDirectoryDefaultsToBuildWhenAbsent() throws Exception
	{
		Path dir = writeManifest("{ \"targets\": {} }");
		assertEquals("build", C3ProjectManifest.outputDirectory(dir.toString()));
	}

	@Test
	public void outputDirectoryDefaultsWhenNoManifest()
	{
		assertEquals("build", C3ProjectManifest.outputDirectory("/no/such/directory/here"));
	}

	@Test
	public void findsOnlyExecutableTargets() throws Exception
	{
		Path dir = writeManifest(
			"{ \"targets\": { \"stunk\": { \"type\": \"executable\" }, "
				+ "\"stunklib\": { \"type\": \"static-lib\" } } }");
		assertEquals(List.of("stunk"), C3ProjectManifest.findExecutableTargets(dir.toString()));
	}

	@Test
	public void dependencyLibrariesFollowSearchPaths() throws Exception
	{
		// Layout of c3script + sibling mystd: "../mystd" holds mystd.c3l as a directory.
		Path workspace = Files.createTempDirectory("c3-deps");
		Path project = Files.createDirectories(workspace.resolve("c3script"));
		Files.createDirectories(project.resolve("lib"));
		Path mystd = Files.createDirectories(workspace.resolve("mystd/mystd.c3l"));
		Files.writeString(project.resolve("project.json"),
			"{ \"dependency-search-paths\": [\"lib\", \"../mystd\"], \"dependencies\": [\"mystd\", \"missing\"] }");

		assertEquals(List.of(mystd), C3ProjectManifest.dependencyLibraries(project.toString()));
	}

	@Test
	public void dependencyLibrariesDefaultToLibAndPreferFirstSearchPath() throws Exception
	{
		Path project = Files.createTempDirectory("c3-deps-default");
		Path archive = Files.createDirectories(project.resolve("lib")).resolve("curl.c3l");
		Files.writeString(archive, "zip");
		Files.writeString(project.resolve("project.json"), "{ \"dependencies\": [\"curl\"] }");

		assertEquals(List.of(archive), C3ProjectManifest.dependencyLibraries(project.toString()));
	}

	@Test
	public void dependencyLibrariesEmptyWithoutManifest()
	{
		assertEquals(List.of(), C3ProjectManifest.dependencyLibraries("/no/such/directory/here"));
	}

	private static final String WORKSPACE = """
		{ "sources": [], "targets": {
		    "workspace": { "type": "executable", "sources": ["src/**"], "test-sources": ["test/**"] },
		    "git-check": { "type": "executable", "sources": ["apps/git-check/src/**"], "test-sources": ["apps/git-check/test/**"] },
		    "git-test": { "type": "test", "test-sources": ["lib/git.c3l/test/**"] },
		    "plain": { "type": "executable", "sources": ["./tools/"] } } }
		""";

	@Test
	public void findTargetForFileMatchesTheTargetSources() throws Exception
	{
		String dir = writeManifest(WORKSPACE).toString();
		assertEquals("git-check", C3ProjectManifest.findTargetForFile(dir, "apps/git-check/src/main.c3", "executable", false));
		assertEquals("git-check", C3ProjectManifest.findTargetForFile(dir, "apps/git-check/src/a/b.c3", "executable", false));
		assertEquals("workspace", C3ProjectManifest.findTargetForFile(dir, "src/main.c3", "executable", false));
		assertEquals("plain", C3ProjectManifest.findTargetForFile(dir, "tools/x.c3", "executable", false));
	}

	@Test
	public void findTargetForFileMatchesTestSources() throws Exception
	{
		String dir = writeManifest(WORKSPACE).toString();
		assertEquals("git-check", C3ProjectManifest.findTargetForFile(dir, "apps/git-check/test/t.c3", null, true));
		assertEquals("git-test", C3ProjectManifest.findTargetForFile(dir, "lib/git.c3l/test/t.c3", null, true));
		assertNull(C3ProjectManifest.findTargetForFile(dir, "lib/git.c3l/test/t.c3", "executable", true));
	}

	@Test
	public void findTargetForFileIsNullWhenNoTargetClaimsIt() throws Exception
	{
		String dir = writeManifest(WORKSPACE).toString();
		assertNull(C3ProjectManifest.findTargetForFile(dir, "other/main.c3", "executable", false));
		assertNull(C3ProjectManifest.findTargetForFile("/no/such/directory/here", "src/main.c3", "executable", false));
	}
}
