package org.c3lang.intellij;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Single configuration type that groups all C3 run configurations (Run Project, Single File,
 * Test, Docgen) under one "C3" node in the New Run Configuration popup. A type with multiple
 * factories is rendered as a group by the platform.
 */
public final class C3ConfigurationType implements ConfigurationType, DumbAware
{
	public static final String ID = "C3";

	private final ConfigurationFactory runProject = new C3BuildRunConfigurationFactory(this);
	private final ConfigurationFactory singleFile = new C3CompileRunConfigurationFactory(this);
	private final ConfigurationFactory test = new C3TestRunConfigurationFactory(this);
	private final ConfigurationFactory docgen = new C3DocgenRunConfigurationFactory(this);

	public static C3ConfigurationType getInstance()
	{
		return ConfigurationTypeUtil.findConfigurationType(C3ConfigurationType.class);
	}

	public @NotNull ConfigurationFactory runProjectFactory() { return runProject; }
	public @NotNull ConfigurationFactory singleFileFactory() { return singleFile; }
	public @NotNull ConfigurationFactory testFactory() { return test; }
	public @NotNull ConfigurationFactory docgenFactory() { return docgen; }

	@Override public @NotNull String getDisplayName() { return "C3"; }

	@Override public String getConfigurationTypeDescription() { return "C3 run configurations"; }

	@Override public Icon getIcon() { return C3Icons.FILE; }

	@Override public @NotNull @NonNls String getId() { return ID; }

	@Override public ConfigurationFactory[] getConfigurationFactories()
	{
		return new ConfigurationFactory[]{ runProject, singleFile, test, docgen };
	}
}
