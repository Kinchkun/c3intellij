package org.c3lang.intellij.debugger;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Extracts the bundled {@code c3.py} LLDB formatter script to a temporary file so it
 * can be imported into a debug session via {@code command script import}.
 */
final class C3LldbFormatters
{
	private static final Logger LOG = Logger.getInstance(C3LldbFormatters.class);

	private static volatile String cachedPath;

	private C3LldbFormatters()
	{
	}

	/** Absolute path to a {@code c3.py} on disk, or {@code null} if it can't be provided. */
	static synchronized @Nullable String scriptPath()
	{
		if (cachedPath != null) return cachedPath;

		try (InputStream in = C3LldbFormatters.class.getResourceAsStream("/lldb/c3.py"))
		{
			if (in == null)
			{
				LOG.warn("Bundled /lldb/c3.py not found");
				return null;
			}
			Path dir = Files.createTempDirectory("c3-lldb");
			Path file = dir.resolve("c3.py");
			Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
			file.toFile().deleteOnExit();
			cachedPath = file.toString();
			return cachedPath;
		}
		catch (Exception e)
		{
			LOG.warn("Failed to materialise c3.py LLDB formatter", e);
			return null;
		}
	}
}
