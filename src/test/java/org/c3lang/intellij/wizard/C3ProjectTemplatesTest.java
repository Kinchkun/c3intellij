package org.c3lang.intellij.wizard;

import org.c3lang.intellij.C3ProjectManifest;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verifies the files created by the "New Project" C3 project/library generators. */
public class C3ProjectTemplatesTest
{
	@Test
	public void moduleNameIsSanitized()
	{
		assertEquals("my_project", C3ProjectTemplates.toModuleName("My Project"));
		assertEquals("hello_world", C3ProjectTemplates.toModuleName("hello-world!"));
		assertEquals("m_2d_game", C3ProjectTemplates.toModuleName("2d game"));
		assertEquals("main", C3ProjectTemplates.toModuleName("---"));
		assertEquals("c3_workspace", C3ProjectTemplates.toModuleName("c3-workspace"));
	}

	@Test
	public void createsExecutableProject() throws Exception
	{
		Path dir = Files.createTempDirectory("c3-new-project");
		String main = C3ProjectTemplates.createProject(dir, "My App");

		assertEquals("src/main.c3", main);
		assertTrue(Files.readString(dir.resolve(main)).startsWith("module my_app;"));
		assertEquals(List.of("my_app"), C3ProjectManifest.findExecutableTargets(dir.toString()));
		for (String sub : List.of("src", "test", "lib", "build", "README.md", "LICENSE"))
		{
			assertTrue(sub, Files.exists(dir.resolve(sub)));
		}
	}

	@Test
	public void createsLibrary() throws Exception
	{
		Path dir = Files.createTempDirectory("c3-new-lib");
		String iface = C3ProjectTemplates.createLibrary(dir, "My Lib");

		assertEquals("my_lib.c3i", iface);
		assertTrue(Files.readString(dir.resolve(iface)).startsWith("module my_lib;"));
		assertTrue(Files.readString(dir.resolve("manifest.json")).contains("\"provides\" : \"my_lib\""));
		assertTrue(Files.isDirectory(dir.resolve("macos-aarch64")));
	}

	@Test
	public void doesNotOverwriteExistingFiles() throws Exception
	{
		Path dir = Files.createTempDirectory("c3-existing");
		Files.writeString(dir.resolve("README.md"), "keep me");
		C3ProjectTemplates.createProject(dir, "x");
		assertEquals("keep me", Files.readString(dir.resolve("README.md")));
	}
}
