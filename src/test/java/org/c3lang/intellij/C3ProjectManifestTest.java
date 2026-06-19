package org.c3lang.intellij;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;

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
}
