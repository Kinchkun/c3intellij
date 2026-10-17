package org.c3lang.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Creates (or reuses) and launches the C3 run configurations for project targets, their tests and
 * the tests of directory libraries. Shared by the gutter icons ({@link C3LineMarkerProvider}) and
 * the Targets tool window.
 */
public final class C3TargetRunner
{
	private C3TargetRunner()
	{
	}

	/** Runs the executable {@code target} of the C3 project in {@code projectDir} ({@code c3c run <target>}). */
	public static void runTarget(@NotNull Project project, @NotNull VirtualFile projectDir, @Nullable String target,
	                             @NotNull Executor executor)
	{
		String name = configName(project, projectDir, target != null ? target : "main");
		launch(project, name, C3ConfigurationType.getInstance().runProjectFactory(), executor, configuration -> {
			C3BuildRunConfiguration config = (C3BuildRunConfiguration) configuration;
			config.setWorkingDirectory(projectDir.getPath());
			if (target != null) config.setTarget(target);
			if (config.getArgs() == null) config.setArgs("");
		});
	}

	/**
	 * Runs the tests of {@code target} (or of {@code c3c}'s default target when null), restricted to
	 * those whose {@code module::name} contains {@code filter} when given ({@code c3c test --test-filter}).
	 */
	public static void runTests(@NotNull Project project, @NotNull VirtualFile projectDir, @Nullable String target,
	                            @Nullable String filter, @NotNull String name, @NotNull Executor executor)
	{
		StringBuilder args = new StringBuilder();
		if (target != null) args.append(target);
		if (filter != null) args.append(args.isEmpty() ? "" : " ").append("--test-filter ").append(filter);

		launch(project, configName(project, projectDir, name), C3ConfigurationType.getInstance().testFactory(), executor,
			configuration -> {
				C3CommandRunConfiguration config = (C3CommandRunConfiguration) configuration;
				config.setWorkingDirectory(projectDir.getPath());
				config.setArgs(args.toString());
			});
	}

	/**
	 * Runs the tests of the directory library {@code library} ({@code lib/argparse.c3l}). Its {@code test/}
	 * folder is no part of any project target, so compile the library plus its tests directly:
	 * {@code c3c compile-test --libdir lib --lib argparse lib/argparse.c3l/test/*.c3 --test-filter ...}.
	 * Returns false when there are no test files to compile.
	 */
	public static boolean runLibraryTests(@NotNull Project project, @NotNull VirtualFile library,
	                                      @Nullable String filter, @NotNull String name)
	{
		String args = libraryTestArgs(library, filter);
		if (args == null) return false;

		launch(project, library.getName() + ": " + name, C3ConfigurationType.getInstance().libraryTestFactory(),
			DefaultRunExecutor.getRunExecutorInstance(), configuration -> {
				C3CommandRunConfiguration config = (C3CommandRunConfiguration) configuration;
				config.setWorkingDirectory(libraryWorkingDirectory(library).getPath());
				config.setArgs(args);
			});
		return true;
	}

	/** The {@code c3c compile-test} arguments for {@link #runLibraryTests}, or null without test files. */
	static @Nullable String libraryTestArgs(@NotNull VirtualFile library, @Nullable String filter)
	{
		List<String> testFiles = libraryTestFiles(library);
		if (testFiles.isEmpty()) return null;

		VirtualFile workingDirectory = libraryWorkingDirectory(library);
		List<String> relative = new ArrayList<>();
		for (String path : testFiles)
		{
			VirtualFile file = library.getFileSystem().findFileByPath(path);
			relative.add(file != null ? VfsUtilCore.getRelativePath(file, workingDirectory) : path);
		}

		String libraryName = library.getNameWithoutExtension();
		String libDir = VfsUtilCore.getRelativePath(library.getParent(), workingDirectory);
		return "--libdir " + (libDir == null || libDir.isEmpty() ? "." : libDir)
			+ " --lib " + libraryName
			+ " " + String.join(" ", relative)
			+ " --build-dir build -o build/" + libraryName + "_test"
			+ (filter != null ? " --test-filter " + filter : "");
	}

	/** Paths of the {@code .c3} files below the {@code test/} folder of {@code library}, sorted. */
	public static @NotNull List<String> libraryTestFiles(@NotNull VirtualFile library)
	{
		List<String> result = new ArrayList<>();
		VirtualFile testDir = library.findChild("test");
		if (testDir == null) return result;
		VfsUtilCore.iterateChildrenRecursively(testDir, null, child -> {
			if (!child.isDirectory() && "c3".equals(child.getExtension())) result.add(child.getPath());
			return true;
		});
		Collections.sort(result);
		return result;
	}

	/** The owning C3 project when the library lives inside it, otherwise the folder holding the library. */
	private static @NotNull VirtualFile libraryWorkingDirectory(@NotNull VirtualFile library)
	{
		VirtualFile projectDir = C3Projects.findProjectDir(library.getParent());
		return projectDir != null ? projectDir : library.getParent();
	}

	/** {@code name}, prefixed with the C3 project's folder name when the IDE project holds several. */
	private static @NotNull String configName(@NotNull Project project, @Nullable VirtualFile projectDir, @NotNull String name)
	{
		String label = C3Projects.label(project, projectDir);
		return label != null ? label + ": " + name : name;
	}

	/**
	 * Reuses the configuration named {@code name} of {@code factory} (so repeated runs don't pile up
	 * duplicates and keep the user's edits, e.g. program arguments), or creates it; then selects and
	 * launches it with {@code executor}.
	 */
	private static void launch(@NotNull Project project, @NotNull String name, @NotNull ConfigurationFactory factory,
	                           @NotNull Executor executor, @NotNull Consumer<RunConfiguration> configure)
	{
		RunManager runManager = RunManager.getInstance(project);
		RunnerAndConfigurationSettings settings = null;
		for (RunnerAndConfigurationSettings existing : runManager.getAllSettings())
		{
			if (existing.getName().equals(name) && existing.getFactory() == factory)
			{
				settings = existing;
				break;
			}
		}
		if (settings == null)
		{
			settings = runManager.createConfiguration(name, factory);
			runManager.addConfiguration(settings);
		}
		configure.accept(settings.getConfiguration());
		runManager.setSelectedConfiguration(settings);

		try
		{
			ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings);
			if (builder == null)
			{
				throw new ExecutionException("'" + name + "' cannot be run with " + executor.getActionName());
			}
			builder.buildAndExecute();
		}
		catch (ExecutionException e)
		{
			ExecutionUtil.handleExecutionError(project, executor.getToolWindowId(), name, e);
		}
	}
}
