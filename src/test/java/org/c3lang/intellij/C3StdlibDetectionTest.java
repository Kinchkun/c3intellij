package org.c3lang.intellij;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** When the standard library is (re)detected on project open, and when a configured path is left alone. */
public class C3StdlibDetectionTest
{
	private static C3SettingsState settings(String sdk, String stdlibPath, String detectedFor)
	{
		C3SettingsState settings = new C3SettingsState();
		settings.sdk = sdk;
		settings.stdlibPath = stdlibPath;
		settings.stdlibDetectedFor = detectedFor;
		return settings;
	}

	@Test
	public void nothingConfiguredNeedsDetection()
	{
		assertTrue(settings("c3c", "", "").needsStdlibDetection());
		assertTrue(settings("c3c", null, null).needsStdlibDetection());
	}

	@Test
	public void handEnteredExistingPathIsKept() throws Exception
	{
		Path lib = Files.createTempDirectory("lib");
		assertFalse(settings("c3c", lib.toString(), "").needsStdlibDetection());
		assertFalse(settings("/other/c3c", lib.toString(), "").needsStdlibDetection());
	}

	@Test
	public void detectedPathIsKeptForTheSameCompilerAndRedetectedForAnother() throws Exception
	{
		Path lib = Files.createTempDirectory("lib");
		assertFalse(settings("c3c", lib.toString(), "c3c").needsStdlibDetection());
		assertTrue(settings("/opt/homebrew/bin/c3c", lib.toString(), "c3c").needsStdlibDetection());
	}

	@Test
	public void vanishedPathIsRedetected() throws Exception
	{
		Path lib = Files.createTempDirectory("lib");
		Files.delete(lib);
		assertTrue(settings("c3c", lib.toString(), "").needsStdlibDetection());
		assertTrue(settings("c3c", lib.toString(), "c3c").needsStdlibDetection());
	}

	@Test
	public void storingRecordsHowThePathWasSet()
	{
		C3SettingsState settings = settings("/usr/local/bin/c3c", "", "");
		settings.setDetectedStdlib("/usr/local/lib/c3");
		assertEquals("/usr/local/lib/c3", settings.stdlibPath);
		assertEquals("/usr/local/bin/c3c", settings.stdlibDetectedFor);

		settings.setManualStdlib("/home/me/c3c/lib");
		assertEquals("/home/me/c3c/lib", settings.stdlibPath);
		assertEquals("", settings.stdlibDetectedFor);
	}
}
