package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.execution.ExecutionException;
import org.c3lang.intellij.psi.C3Attribute;
import org.c3lang.intellij.psi.C3AttributeName;
import org.c3lang.intellij.psi.C3Attributes;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3FuncName;
import org.c3lang.intellij.psi.C3Module;
import org.c3lang.intellij.psi.C3ModuleSection;
import org.c3lang.intellij.psi.C3Types;
import org.jetbrains.annotations.Nullable;

public class C3LineMarkerProvider implements LineMarkerProvider
{
	@Override
	public @Nullable LineMarkerInfo<?> getLineMarkerInfo(PsiElement element)
	{
		// Line markers must be anchored on leaf elements (per LineMarkerProvider's contract). The
		// C3 function name is the trailing IDENT leaf of func_name (`(type DOT)? IDENT`), so fire
		// only for that leaf and anchor the gutter icon there.
		if (element.getNode() == null) return null;

		// A play button on the `module` keyword of a module holding tests runs all of them
		// (c3c test --test-filter <module>::).
		if (element.getNode().getElementType() == C3Types.KW_MODULE) return moduleTestsMarker(element);

		if (element.getNode().getElementType() != C3Types.IDENT) return null;
		if (!(element.getParent() instanceof C3FuncName)) return null;

		C3FuncDef funcDef = PsiTreeUtil.getParentOfType(element, C3FuncDef.class);
		if (funcDef == null) return null;

		String name = funcDef.getFqName().getName();

		// A play button on @test functions runs just that test (c3c test --test-filter <module>::<name>).
		if (isTest(funcDef))
		{
			String filter = funcDef.getFqName().getModule() != null ? funcDef.getFqName().getFullName() : name;
			return new LineMarkerInfo<>(
				element,
				element.getTextRange(),
				AllIcons.RunConfigurations.TestState.Run,
				ignored -> "Run test '" + name + "'",
				(event, elt) -> runTests(elt.getProject(), fileOf(elt), "Test " + name, filter),
				GutterIconRenderer.Alignment.RIGHT,
				() -> "Run test '" + name + "'"
			);
		}

		String type = funcDef.getFuncHeader().getOptionalType().getType().getText();

		if (!name.equals("main")) return null;
		if (!type.equals("int") && !type.equals("void")) return null;

		return new LineMarkerInfo<>(
			element,
			element.getTextRange(),
			AllIcons.Actions.Execute,
			ignored -> "Run main",
			(event, elt) -> {
				VirtualFile file = elt.getContainingFile() != null ? elt.getContainingFile().getVirtualFile() : null;
				if (file != null && ScratchUtil.isScratch(file))
				{
					runScratch(elt.getProject(), file);
				}
				else
				{
					createAndRunCustomConfig(elt.getProject(), file);
				}
			},
			GutterIconRenderer.Alignment.RIGHT,
			() -> "Click to open context menu"
		);
	}

	private static @Nullable LineMarkerInfo<?> moduleTestsMarker(PsiElement element)
	{
		if (!(element.getParent() instanceof C3Module module)) return null;
		if (!(module.getParent() instanceof C3ModuleSection section)) return null;
		if (!containsTests(section)) return null;

		String moduleName = module.getModulePath().getText();
		return new LineMarkerInfo<>(
			element,
			element.getTextRange(),
			AllIcons.RunConfigurations.TestState.Run_run,
			ignored -> "Run tests in '" + moduleName + "'",
			(event, elt) -> runTests(elt.getProject(), fileOf(elt), "Tests in " + moduleName, moduleName + "::"),
			GutterIconRenderer.Alignment.RIGHT,
			() -> "Run tests in '" + moduleName + "'"
		);
	}

	/** Whether the module section declares at least one test function. */
	private static boolean containsTests(C3ModuleSection section)
	{
		boolean moduleIsTest = hasTestAttribute(section.getModule().getAttributes());
		for (C3FuncDef funcDef : PsiTreeUtil.findChildrenOfType(section, C3FuncDef.class))
		{
			if (moduleIsTest || hasTestAttribute(funcDef.getAttributes())) return true;
		}
		return false;
	}

	/** A function is a test when it, or its module ({@code module foo @test;}), is marked {@code @test}. */
	private static boolean isTest(C3FuncDef funcDef)
	{
		if (hasTestAttribute(funcDef.getAttributes())) return true;
		C3ModuleSection section = PsiTreeUtil.getParentOfType(funcDef, C3ModuleSection.class);
		return section != null && hasTestAttribute(section.getModule().getAttributes());
	}

	private static boolean hasTestAttribute(@Nullable C3Attributes attributes)
	{
		if (attributes == null) return false;

		for (C3Attribute attribute : attributes.getAttributeList())
		{
			C3AttributeName attributeName = attribute.getAttributeName();
			if (attributeName == null) continue;

			String text = attributeName.getText();
			if (text != null && text.replace("@", "").equals("test")) return true;
		}
		return false;
	}

	private static @Nullable VirtualFile fileOf(PsiElement element)
	{
		return element.getContainingFile() != null ? element.getContainingFile().getVirtualFile() : null;
	}

	/**
	 * The working directory for running {@code file}: its C3 project (nearest {@code project.json}),
	 * so a workspace holding several C3 projects runs the right one.
	 */
	private static String workingDirectory(Project project, @Nullable VirtualFile file)
	{
		VirtualFile dir = C3Projects.findProjectDir(file);
		return dir != null ? dir.getPath() : project.getBasePath();
	}

	/**
	 * The {@code project.json} target that compiles {@code file} (see {@link C3ProjectManifest#findTargetForFile}),
	 * e.g. {@code git-check} for {@code apps/git-check/src/main.c3}, or {@code null} for {@code c3c}'s default.
	 */
	private static @Nullable String targetOf(@Nullable String workingDirectory, @Nullable VirtualFile file,
	                                         @Nullable String type, boolean testSources)
	{
		if (workingDirectory == null || file == null) return null;
		VirtualFile root = file.getFileSystem().findFileByPath(workingDirectory);
		String relative = root != null ? VfsUtilCore.getRelativePath(file, root) : null;
		return relative != null ? C3ProjectManifest.findTargetForFile(workingDirectory, relative, type, testSources) : null;
	}

	/** {@code name}, prefixed with the C3 project's folder name when the IDE project holds several. */
	private static String configName(Project project, @Nullable VirtualFile file, String name)
	{
		String label = C3Projects.label(project, C3Projects.findProjectDir(file));
		return label != null ? label + ": " + name : name;
	}

	/** Runs the tests whose {@code module::name} contains {@code filter} ({@code c3c test --test-filter}). */
	private static void runTests(Project project, @Nullable VirtualFile file, String name, String filter)
	{
		RunManager runManager = RunManager.getInstance(project);
		ConfigurationFactory factory = C3ConfigurationType.getInstance().testFactory();

		RunnerAndConfigurationSettings settings =
			runManager.createConfiguration(configName(project, file, name), factory);
		C3CommandRunConfiguration config = (C3CommandRunConfiguration) settings.getConfiguration();

		String workingDirectory = workingDirectory(project, file);
		String target = targetOf(workingDirectory, file, null, true);

		config.setWorkingDirectory(workingDirectory);
		config.setArgs((target != null ? target + " " : "") + "--test-filter " + filter);

		runManager.addConfiguration(settings);
		runManager.setSelectedConfiguration(settings);

		run(project, settings);
	}

	private static void runScratch(Project project, VirtualFile file)
	{
		RunManager runManager = RunManager.getInstance(project);
		ConfigurationFactory factory = C3ConfigurationType.getInstance().singleFileFactory();

		RunnerAndConfigurationSettings settings =
			runManager.createConfiguration(file.getName(), factory);
		C3CompileRunConfiguration config = (C3CompileRunConfiguration) settings.getConfiguration();

		config.setSourceFile(file.getPath());
		VirtualFile parent = file.getParent();
		config.setWorkingDirectory(parent != null ? parent.getPath() : project.getBasePath());
		config.setArgs("");

		runManager.addConfiguration(settings);
		runManager.setSelectedConfiguration(settings);

		run(project, settings);
	}

	private static void createAndRunCustomConfig(Project project, @Nullable VirtualFile file)
	{
		RunManager runManager = RunManager.getInstance(project);
		ConfigurationFactory factory = C3ConfigurationType.getInstance().runProjectFactory();
		String workingDirectory = workingDirectory(project, file);
		String target = targetOf(workingDirectory, file, "executable", false);
		var settings = runManager.createConfiguration(configName(project, file, target != null ? target : "main"), factory);
		C3BuildRunConfiguration config = (C3BuildRunConfiguration) settings.getConfiguration();

		config.setWorkingDirectory(workingDirectory);
		if (target != null) config.setTarget(target);
		config.setArgs("");

		runManager.addConfiguration(settings);
		runManager.setSelectedConfiguration(settings);

		run(project, settings);
	}

	private static void run(Project project, RunnerAndConfigurationSettings settings)
	{
		var executor = DefaultRunExecutor.getRunExecutorInstance();
		try
		{
			ExecutionEnvironment environment = ExecutionEnvironmentBuilder.create(executor, settings).build();
			environment.getRunner().execute(environment);
		}
		catch (ExecutionException e)
		{
			throw new RuntimeException(e);
		}
	}
}
