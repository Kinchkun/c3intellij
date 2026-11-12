package org.c3lang.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Finds the C3 standard library the way the compiler itself does.
 *
 * <p>Every result is the directory that contains {@code std}, which is what c3c prints and what
 * {@code C3C_LIB} names. {@link C3SettingsState#stdlibRoot()} narrows it to {@code std} for indexing.</p>
 *
 * <ol>
 *   <li>Ask the configured compiler: {@code c3c compile --build-env} prints a {@code Stdlib} line
 *   (which already honors {@code C3C_LIB}, since the compiler applies it).</li>
 *   <li>{@code C3C_LIB} from the environment, when the compiler could not be run.</li>
 *   <li>The compiler's own search next to its executable (see {@link #SEARCH_ORDER}).</li>
 * </ol>
 */
public final class C3StdlibLocator
{
	private static final Logger LOG = Logger.getInstance(C3StdlibLocator.class);
	static final int BUILD_ENV_TIMEOUT_MS = 10_000;

	/**
	 * c3c's candidates relative to the directory of its (symlink-resolved) executable, in the
	 * order of {@code find_lib_dir} in {@code src/utils/file_utils.c}. The first one containing
	 * {@code std} wins. Homebrew's layout ({@code libexec/c3c} next to {@code lib/c3/std}) is the
	 * first entry, a release archive or a source checkout the second or fourth.
	 */
	static final List<String> SEARCH_ORDER = List.of(
		"../lib/c3", "../lib", "lib/c3", "lib", "c3", ".", "../c3", "..", "../../lib/c3", "../../lib");

	private C3StdlibLocator()
	{
	}

	/**
	 * The standard library for the "C3 Compiler" setting ({@code compilerSetting}: a path or a
	 * name on the PATH), or null when nothing was found.
	 */
	public static @Nullable Path detect(@NotNull String compilerSetting)
	{
		Path compiler = resolveCompiler(compilerSetting);
		if (compiler != null)
		{
			Path fromCompiler = askCompiler(compiler);
			if (fromCompiler != null) return fromCompiler;
		}
		Path fromEnvironment = fromEnvironment(EnvironmentUtil.getEnvironmentMap());
		if (fromEnvironment != null) return fromEnvironment;
		return compiler != null ? nearExecutable(compiler) : null;
	}

	/**
	 * The compiler executable for the "C3 Compiler" setting: a path as given when it is
	 * executable, otherwise a name looked up in the PATH of the login shell (so a Homebrew install
	 * is found even when the IDE was started from the Dock).
	 */
	public static @Nullable Path resolveCompiler(@NotNull String setting)
	{
		return resolveCompiler(setting, EnvironmentUtil.getValue("PATH"));
	}

	/** {@link #resolveCompiler(String)} against a given PATH value. */
	static @Nullable Path resolveCompiler(@NotNull String setting, @Nullable String pathVariable)
	{
		String name = setting.trim();
		if (name.isEmpty()) name = "c3c";
		if (name.contains("/") || name.contains("\\")) return executableAt(Paths.get(name));
		if (pathVariable == null) return null;
		for (String entry : pathVariable.split(File.pathSeparator))
		{
			if (entry.isBlank()) continue;
			try
			{
				Path directory = Paths.get(entry.trim());
				Path found = executableAt(directory.resolve(name));
				if (found == null && SystemInfo.isWindows) found = executableAt(directory.resolve(name + ".exe"));
				if (found != null) return found;
			}
			catch (InvalidPathException ignored)
			{
			}
		}
		return null;
	}

	private static @Nullable Path executableAt(@NotNull Path path)
	{
		try
		{
			return Files.isExecutable(path) && !Files.isDirectory(path) ? path : null;
		}
		catch (InvalidPathException e)
		{
			return null;
		}
	}

	/** Runs {@code c3c compile --build-env} and returns the directory of its {@code Stdlib} line when that contains {@code std}. */
	static @Nullable Path askCompiler(@NotNull Path compiler)
	{
		try
		{
			GeneralCommandLine command = new GeneralCommandLine(compiler.toString(), "compile", "--build-env")
				.withWorkDirectory(System.getProperty("java.io.tmpdir"));
			ProcessOutput output = ExecUtil.execAndGetOutput(command, BUILD_ENV_TIMEOUT_MS);
			if (output.isTimeout())
			{
				LOG.warn("'" + command.getCommandLineString() + "' did not finish within " + BUILD_ENV_TIMEOUT_MS + " ms");
				return null;
			}
			Path path = parseBuildEnv(output.getStdout());
			if (path == null) path = parseBuildEnv(output.getStderr());
			return path != null && containsStd(path) ? path : null;
		}
		catch (ExecutionException e)
		{
			LOG.warn("Could not run the C3 compiler '" + compiler + "' to find the standard library: " + e.getMessage());
			return null;
		}
	}

	/**
	 * The value of the {@code Stdlib} line of {@code c3c compile --build-env}, made absolute and
	 * normalized (the compiler prints it as {@code <exe dir>/../lib/c3/}). Null when the output has
	 * no such line.
	 */
	public static @Nullable Path parseBuildEnv(@NotNull String output)
	{
		for (String line : output.split("\\R"))
		{
			String trimmed = line.trim();
			if (!trimmed.startsWith("Stdlib")) continue;
			int colon = trimmed.indexOf(':');
			if (colon < 0) continue;
			String value = trimmed.substring(colon + 1).trim();
			if (value.isEmpty()) return null;
			try
			{
				return Paths.get(value).toAbsolutePath().normalize();
			}
			catch (InvalidPathException e)
			{
				return null;
			}
		}
		return null;
	}

	/**
	 * {@code C3C_LIB}, which the compiler prefers over everything else, when it names an existing
	 * directory. Like the compiler, this trusts the variable and does not look for {@code std} in it.
	 */
	public static @Nullable Path fromEnvironment(@NotNull Map<String, String> environment)
	{
		String value = environment.get("C3C_LIB");
		if (value == null || value.isBlank()) return null;
		try
		{
			Path path = Paths.get(value.trim()).toAbsolutePath().normalize();
			return Files.isDirectory(path) ? path : null;
		}
		catch (InvalidPathException e)
		{
			return null;
		}
	}

	/**
	 * The standard library next to a compiler executable, in the compiler's own
	 * {@link #SEARCH_ORDER}. Symlinks are resolved first, so Homebrew's {@code bin/c3c} link leads
	 * to the keg. Null when no candidate contains {@code std}.
	 */
	public static @Nullable Path nearExecutable(@NotNull Path executable)
	{
		Path real;
		try
		{
			real = executable.toRealPath();
		}
		catch (IOException e)
		{
			real = executable.toAbsolutePath().normalize();
		}
		Path directory = real.getParent();
		if (directory == null) return null;
		for (String candidate : SEARCH_ORDER)
		{
			Path path = directory.resolve(candidate).normalize();
			if (containsStd(path)) return path;
		}
		return null;
	}

	/** Whether {@code directory} is a library root, i.e. has a {@code std} subdirectory. */
	public static boolean containsStd(@NotNull Path directory)
	{
		return Files.isDirectory(directory.resolve("std"));
	}
}
