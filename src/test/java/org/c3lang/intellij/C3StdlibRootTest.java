package org.c3lang.intellij;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

/** A compiler checkout configured as the stdlib path indexes only its {@code lib/std}, not its test suite. */
public class C3StdlibRootTest
{
	private static String rootFor(String stdlibPath)
	{
		C3SettingsState settings = new C3SettingsState();
		settings.stdlibPath = stdlibPath;
		return settings.stdlibRoot();
	}

	@Test
	public void compilerCheckoutIsNarrowedToLibStd() throws Exception
	{
		Path checkout = Files.createTempDirectory("c3c");
		Files.createDirectories(checkout.resolve("lib/std"));
		Files.createDirectories(checkout.resolve("test/test_suite"));
		assertEquals(checkout.resolve("lib/std").toString(), rootFor(checkout.toString()));
	}

	@Test
	public void stdDirectoryIsKeptAsIs() throws Exception
	{
		Path std = Files.createTempDirectory("std");
		assertEquals(std.toString(), rootFor(std.toString()));
	}

	@Test
	public void emptyPathStaysEmpty()
	{
		assertEquals("", rootFor(""));
	}
}
