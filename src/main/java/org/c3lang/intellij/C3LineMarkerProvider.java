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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.execution.ExecutionException;
import org.c3lang.intellij.psi.C3Attribute;
import org.c3lang.intellij.psi.C3AttributeName;
import org.c3lang.intellij.psi.C3Attributes;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3FuncName;
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
		if (element.getNode() == null || element.getNode().getElementType() != C3Types.IDENT) return null;
		if (!(element.getParent() instanceof C3FuncName)) return null;

		C3FuncDef funcDef = PsiTreeUtil.getParentOfType(element, C3FuncDef.class);
		if (funcDef == null) return null;

		String name = funcDef.getFqName().getName();

		// A play button on @test functions runs just that test (c3c test --test-filter <name>).
		if (hasTestAttribute(funcDef))
		{
			return new LineMarkerInfo<>(
				element,
				element.getTextRange(),
				AllIcons.RunConfigurations.TestState.Run,
				ignored -> "Run test '" + name + "'",
				(event, elt) -> runSingleTest(elt.getProject(), name),
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
					createAndRunCustomConfig(elt.getProject());
				}
			},
			GutterIconRenderer.Alignment.RIGHT,
			() -> "Click to open context menu"
		);
	}

	private static boolean hasTestAttribute(C3FuncDef funcDef)
	{
		C3Attributes attributes = funcDef.getAttributes();
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

	private static void runSingleTest(Project project, String testName)
	{
		RunManager runManager = RunManager.getInstance(project);
		ConfigurationFactory factory = C3ConfigurationType.getInstance().testFactory();

		RunnerAndConfigurationSettings settings =
			runManager.createConfiguration("Test " + testName, factory);
		C3CommandRunConfiguration config = (C3CommandRunConfiguration) settings.getConfiguration();

		config.setWorkingDirectory(project.getBasePath());
		config.setArgs("--test-filter " + testName);

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

	private static void createAndRunCustomConfig(Project project)
	{
		RunManager runManager = RunManager.getInstance(project);
		ConfigurationFactory factory = C3ConfigurationType.getInstance().runProjectFactory();
		var settings = runManager.createConfiguration("main", factory);
		C3BuildRunConfiguration config = (C3BuildRunConfiguration) settings.getConfiguration();

		config.setWorkingDirectory(project.getBasePath());
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
