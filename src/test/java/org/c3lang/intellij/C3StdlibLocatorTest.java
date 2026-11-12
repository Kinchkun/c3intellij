package org.c3lang.intellij;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** {@link C3StdlibLocator} finds the standard library where c3c itself looks, without running it. */
public class C3StdlibLocatorTest
{
	@Test
	public void buildEnvStdlibLineIsNormalized()
	{
		String output = "Version           : 0.8.6\n"
			+ "Stdlib            : /opt/c3c/libexec/../lib/c3/\n"
			+ "Exe name          : c3c\n";
		assertEquals(Paths.get("/opt/c3c/lib/c3"), C3StdlibLocator.parseBuildEnv(output));
	}

	@Test
	public void outputWithoutStdlibLineGivesNull()
	{
		assertNull(C3StdlibLocator.parseBuildEnv("Version : 0.8.6\nExe name : c3c\n"));
		assertNull(C3StdlibLocator.parseBuildEnv(""));
		assertNull(C3StdlibLocator.parseBuildEnv("Stdlib :   \n"));
	}

	@Test
	public void homebrewKegIsFoundThroughTheSymlinkedExecutable() throws Exception
	{
		Path root = Files.createTempDirectory("homebrew");
		Path keg = root.resolve("Cellar/c3c/0.8.6");
		Files.createDirectories(keg.resolve("libexec"));
		Files.createDirectories(keg.resolve("bin"));
		Files.createDirectories(keg.resolve("lib/c3/std"));
		Files.createDirectories(root.resolve("bin"));
		Files.createFile(keg.resolve("libexec/c3c"));
		Files.createSymbolicLink(keg.resolve("bin/c3c"), Paths.get("../libexec/c3c"));
		Files.createSymbolicLink(root.resolve("bin/c3c"), Paths.get("../Cellar/c3c/0.8.6/bin/c3c"));

		assertEquals(keg.resolve("lib/c3").toRealPath(), C3StdlibLocator.nearExecutable(root.resolve("bin/c3c")));
	}

	@Test
	public void releaseArchiveKeepsLibNextToTheExecutable() throws Exception
	{
		Path dir = Files.createTempDirectory("c3c");
		Files.createDirectories(dir.resolve("lib/std"));
		Files.createFile(dir.resolve("c3c"));
		assertEquals(dir.resolve("lib").toRealPath(), C3StdlibLocator.nearExecutable(dir.resolve("c3c")));
	}

	@Test
	public void sourceCheckoutHasTheExecutableInBuild() throws Exception
	{
		Path dir = Files.createTempDirectory("c3c");
		Files.createDirectories(dir.resolve("lib/std"));
		Files.createDirectories(dir.resolve("build"));
		Files.createFile(dir.resolve("build/c3c"));
		assertEquals(dir.resolve("lib").toRealPath(), C3StdlibLocator.nearExecutable(dir.resolve("build/c3c")));
	}

	@Test
	public void executableWithoutStdlibNearbyGivesNull() throws Exception
	{
		Path dir = Files.createTempDirectory("bin");
		Files.createFile(dir.resolve("c3c"));
		assertNull(C3StdlibLocator.nearExecutable(dir.resolve("c3c")));
	}

	@Test
	public void environmentVariableIsTrustedWhenTheDirectoryExists() throws Exception
	{
		Path lib = Files.createTempDirectory("c3lib");
		assertEquals(lib.toAbsolutePath().normalize(), C3StdlibLocator.fromEnvironment(Map.of("C3C_LIB", lib.toString())));
		assertNull(C3StdlibLocator.fromEnvironment(Map.of("C3C_LIB", lib.resolve("missing").toString())));
		assertNull(C3StdlibLocator.fromEnvironment(Map.of("C3C_LIB", "  ")));
		assertNull(C3StdlibLocator.fromEnvironment(Map.of()));
	}

	@Test
	public void compilerSettingWithAPathMustBeAnExecutableFile() throws Exception
	{
		Path dir = Files.createTempDirectory("bin");
		Path c3c = Files.createFile(dir.resolve("c3c"));
		assertNull(C3StdlibLocator.resolveCompiler(c3c.toString()));
		if (!c3c.toFile().setExecutable(true)) return;
		assertEquals(c3c, C3StdlibLocator.resolveCompiler(c3c.toString()));
		assertNull(C3StdlibLocator.resolveCompiler(dir.resolve("missing").toString()));
		assertNull(C3StdlibLocator.resolveCompiler(dir.toString()));
	}

	@Test
	public void compilerNameIsLookedUpInTheGivenPath() throws Exception
	{
		Path empty = Files.createTempDirectory("empty");
		Path bin = Files.createTempDirectory("bin");
		Path c3c = Files.createFile(bin.resolve("c3c"));
		if (!c3c.toFile().setExecutable(true)) return;
		String pathVariable = empty + java.io.File.pathSeparator + bin;
		assertEquals(c3c, C3StdlibLocator.resolveCompiler("c3c", pathVariable));
		assertEquals(c3c, C3StdlibLocator.resolveCompiler("", pathVariable));
		assertNull(C3StdlibLocator.resolveCompiler("c3c", empty.toString()));
		assertNull(C3StdlibLocator.resolveCompiler("c3c", null));
	}
}
