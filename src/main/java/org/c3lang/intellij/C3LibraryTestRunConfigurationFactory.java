package org.c3lang.intellij;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests of a directory library ({@code lib/foo.c3l}): {@code c3c test} only sees the project's own
 * {@code test-sources}, so these compile the library's tests directly with {@code c3c compile-test}.
 */
public class C3LibraryTestRunConfigurationFactory extends ConfigurationFactory
{
    public C3LibraryTestRunConfigurationFactory(@NotNull ConfigurationType type)
    {
        super(type);
    }

    @Override public @NotNull @NonNls String getId()
    {
        return "C3LibraryTestRunConfiguration";
    }

    @Override public @NotNull String getName()
    {
        return "Library Test";
    }

    @Override public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project)
    {
        return new C3CommandRunConfiguration(project, this, "C3 Library Test", "compile-test");
    }

    @Override public @Nullable Class<? extends BaseState> getOptionsClass()
    {
        return C3BuildRunConfigurationOptions.class;
    }
}
