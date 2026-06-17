package org.c3lang.intellij;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class C3TestRunConfigurationFactory extends ConfigurationFactory
{
    public C3TestRunConfigurationFactory(@NotNull ConfigurationType type)
    {
        super(type);
    }

    @Override public @NotNull @NonNls String getId()
    {
        return "C3TestRunConfiguration";
    }

    @Override public @NotNull String getName()
    {
        return "Test";
    }

    @Override public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project)
    {
        return new C3CommandRunConfiguration(project, this, "C3 Test", "test");
    }

    @Override public @Nullable Class<? extends BaseState> getOptionsClass()
    {
        return C3BuildRunConfigurationOptions.class;
    }
}
