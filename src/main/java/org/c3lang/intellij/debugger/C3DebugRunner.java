package org.c3lang.intellij.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.AsyncProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess;
import org.c3lang.intellij.C3BuildRunConfiguration;
import org.c3lang.intellij.C3CommandRunConfiguration;
import org.c3lang.intellij.C3CompileRunConfiguration;
import org.c3lang.intellij.C3ProjectManifest;
import org.c3lang.intellij.C3SettingsState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs "C3 Run Project" configurations under CLion's native LLDB debugger.
 *
 * <p>It first builds the project with {@code c3c build} (which emits debug info for an
 * {@code O0} debug target), parses the linked executable path from the build output,
 * then launches it under {@link CidrLocalDebugProcess}. Breakpoints, watches, variable
 * inspection and setting all come from CLion's native debugger.</p>
 */
public final class C3DebugRunner extends AsyncProgramRunner<RunnerSettings>
{
	private static final Pattern EXECUTABLE_PATTERN =
		Pattern.compile("executable '([^']+)'");

	@Override
	public @NotNull String getRunnerId()
	{
		return "C3DebugRunner";
	}

	@Override
	public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile)
	{
		if (!DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)) return false;
		return profile instanceof C3BuildRunConfiguration
			|| profile instanceof C3CompileRunConfiguration
			|| (profile instanceof C3CommandRunConfiguration command && "test".equals(command.getCommand()));
	}

	@Override
	protected @NotNull Promise<RunContentDescriptor> execute(
		@NotNull ExecutionEnvironment environment, @NotNull RunProfileState state)
	{
		AsyncPromise<RunContentDescriptor> promise = new AsyncPromise<>();

		RunProfile profile = environment.getRunProfile();
		String sdk = C3SettingsState.getInstance().sdk;

		String workingDirectory;
		List<String> buildArguments = new ArrayList<>();
		// Arguments passed to the launched binary. For tests they are baked into the
		// executable at build time (e.g. --test-filter), so the binary is run without them.
		String binaryArgs;

		if (profile instanceof C3BuildRunConfiguration configuration)
		{
			workingDirectory = configuration.getWorkingDirectory();
			buildArguments.add("build");
			// Build the selected target with the configured c3c flags (the "Additional arguments"),
			// mirroring `c3c run <target> <args>`. The debuggee then receives the "Program arguments".
			String target = configuration.getTarget();
			if (target != null && !target.isBlank()) buildArguments.add(target.trim());
			addBuildArgs(buildArguments, configuration.getArgs());
			binaryArgs = configuration.getProgramArgs();
		}
		else if (profile instanceof C3CompileRunConfiguration configuration)
		{
			// Single file (e.g. a scratch): compile it (with debug info) into an executable,
			// then launch that under LLDB.
			workingDirectory = configuration.getWorkingDirectory();
			binaryArgs = configuration.getArgs();
			buildArguments.add("compile");
			buildArguments.add(configuration.getSourceFile());
			buildArguments.add("-O0"); // emit debug info
		}
		else if (profile instanceof C3CommandRunConfiguration configuration && "test".equals(configuration.getCommand()))
		{
			workingDirectory = configuration.getWorkingDirectory();
			binaryArgs = null;
			buildArguments.add("test");
			addBuildArgs(buildArguments, configuration.getArgs());
			buildArguments.add("--suppress-run"); // build the test binary, don't run it
		}
		else
		{
			promise.setError(new ExecutionException("This configuration cannot be debugged."));
			return promise;
		}

		ApplicationManager.getApplication().invokeLater(() ->
			FileDocumentManager.getInstance().saveAllDocuments());

		// Where c3c is expected to write the binary, used when the build output doesn't name the
		// executable (e.g. a quiet `-q` build suppresses the "linked to executable '...'" line).
		final String expectedExecutable = expectedExecutable(profile, workingDirectory);

		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try
			{
				GeneralCommandLine buildCommand = new GeneralCommandLine(sdk)
					.withParameters(buildArguments)
					.withWorkDirectory(workingDirectory);
				ProcessOutput output = ExecUtil.execAndGetOutput(buildCommand);

				if (output.getExitCode() != 0)
				{
					// Don't fall back to a (possibly stale) binary when the build actually failed.
					promise.setError(new ExecutionException(
						"`" + sdk + " " + String.join(" ", buildArguments) + "` failed (exit "
							+ output.getExitCode() + "):\n" + output.getStdout() + output.getStderr()));
					return;
				}

				String executable = parseExecutable(output, workingDirectory);
				if (executable == null) executable = resolveExisting(expectedExecutable);
				if (executable == null)
				{
					promise.setError(new ExecutionException(
						"Could not determine the built C3 executable. `" + sdk + " "
							+ String.join(" ", buildArguments) + "` output:\n"
							+ output.getStdout() + output.getStderr()));
					return;
				}

				GeneralCommandLine runCommand = new GeneralCommandLine(executable)
					.withWorkDirectory(workingDirectory);
				if (binaryArgs != null && !binaryArgs.isEmpty()) runCommand.addParameters(binaryArgs.split(" "));

				ApplicationManager.getApplication().invokeLater(() -> {
					try
					{
						startDebugSession(environment, runCommand);
						// startSessionAndShowTab shows the debug tab itself; returning null
						// avoids the split-mode-deprecated XDebugSession.getRunContentDescriptor().
						promise.setResult(null);
					}
					catch (ExecutionException e)
					{
						promise.setError(e);
					}
				});
			}
			catch (ExecutionException e)
			{
				promise.setError(e);
			}
		});

		return promise;
	}

	private static void startDebugSession(
		@NotNull ExecutionEnvironment environment, @NotNull GeneralCommandLine runCommand) throws ExecutionException
	{
		Project project = environment.getProject();
		C3DebugRunParameters parameters = new C3DebugRunParameters(runCommand);

		XDebuggerManager.getInstance(project).startSessionAndShowTab(
			environment.getRunProfile().getName(),
			new XDebugProcessStarter()
			{
				@Override
				public @NotNull XDebugProcess start(@NotNull XDebugSession session) throws ExecutionException
				{
					TextConsoleBuilder consoleBuilder =
						TextConsoleBuilderFactory.getInstance().createBuilder(project);
					CidrLocalDebugProcess process =
						new CidrLocalDebugProcess(parameters, session, consoleBuilder);
					ProcessTerminatedListener.attach(process.getProcessHandler(), project);
					process.start();
					return process;
				}
			},
			environment);
	}

	/**
	 * Appends the user's c3c flags to the build command, dropping {@code -q}/{@code --quiet}: a quiet
	 * build suppresses the "linked to executable '...'" line the runner relies on, and quiet output is
	 * unwanted when debugging anyway.
	 */
	private static void addBuildArgs(@NotNull List<String> buildArguments, @Nullable String args)
	{
		if (args == null || args.isBlank()) return;
		for (String arg : args.trim().split(" "))
		{
			if (arg.isEmpty() || arg.equals("-q") || arg.equals("--quiet")) continue;
			buildArguments.add(arg);
		}
	}

	/**
	 * The binary path c3c is expected to produce for a build configuration, derived from the
	 * {@code project.json} output directory and the selected target, or null when it can't be
	 * determined (e.g. no explicit target). Used as a fallback when the build output is quiet.
	 */
	private static @Nullable String expectedExecutable(@NotNull RunProfile profile, @NotNull String workingDirectory)
	{
		if (!(profile instanceof C3BuildRunConfiguration configuration)) return null;

		String target = configuration.getTarget();
		if (target == null || target.isBlank()) return null;

		String output = C3ProjectManifest.outputDirectory(workingDirectory);
		return new File(new File(workingDirectory, output), target.trim()).getPath();
	}

	/** Returns {@code path} (or its {@code .exe} variant) if it exists as a file, else null. */
	private static @Nullable String resolveExisting(@Nullable String path)
	{
		if (path == null) return null;
		File file = new File(path);
		if (file.isFile()) return file.getAbsolutePath();
		File windows = new File(path + ".exe");
		return windows.isFile() ? windows.getAbsolutePath() : null;
	}

	private static @Nullable String parseExecutable(@NotNull ProcessOutput output, @NotNull String workingDirectory)
	{
		for (String text : new String[]{ output.getStdout(), output.getStderr() })
		{
			if (text == null) continue;
			Matcher matcher = EXECUTABLE_PATTERN.matcher(text);
			String last = null;
			while (matcher.find()) last = matcher.group(1);
			if (last != null)
			{
				File file = new File(last);
				if (!file.isAbsolute()) file = new File(workingDirectory, last);
				if (file.exists()) return file.getAbsolutePath();
			}
		}
		return null;
	}
}
