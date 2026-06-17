package org.c3lang.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessHandlerFactory;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A project-level run configuration that invokes a single {@code c3c} subcommand
 * (e.g. {@code test}, {@code docgen}) in a working directory with optional extra arguments.
 * The subcommand is fixed by the owning factory, so it is not part of the persisted options.
 */
public class C3CommandRunConfiguration extends RunConfigurationBase<C3BuildRunConfigurationOptions>
{
    private final @NotNull String command;

    protected C3CommandRunConfiguration(Project project, ConfigurationFactory factory, String name, @NotNull String command)
    {
        super(project, factory, name);
        this.command = command;
    }

    @Override public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor()
    {
        return new C3CommandRunEditor();
    }

    @Override protected @NotNull C3BuildRunConfigurationOptions getOptions()
    {
        return (C3BuildRunConfigurationOptions) super.getOptions();
    }

    /** The {@code c3c} subcommand this configuration runs (e.g. {@code test}, {@code docgen}). */
    public @NotNull String getCommand()
    {
        return command;
    }

    public String getWorkingDirectory()
    {
        return getOptions().getWorkingDirectory();
    }

    public void setWorkingDirectory(String workingDirectory)
    {
        getOptions().setWorkingDirectory(workingDirectory);
    }

    public String getArgs()
    {
        return getOptions().getArgs();
    }

    public void setArgs(String args)
    {
        getOptions().setArgs(args);
    }

    @Override public void checkConfiguration()
    {
    }

    @Override public @Nullable RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment executionEnvironment) throws ExecutionException
    {
        return new CommandLineState(executionEnvironment) {
            @Override protected @NotNull ProcessHandler startProcess() throws ExecutionException
            {
                String sdk = C3SettingsState.getInstance().sdk;
                GeneralCommandLine commandLine = new GeneralCommandLine(sdk, command);

                // Split the args by spaces: GeneralCommandLine would otherwise quote a value
                // containing spaces and produce invalid syntax (e.g. c3c test "--param value").
                String args = getArgs();
                if (args != null && !args.isEmpty()) commandLine.addParameters(args.split(" "));

                commandLine.setWorkDirectory(getWorkingDirectory());

                OSProcessHandler processHandler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine);
                ProcessTerminatedListener.attach(processHandler);
                return processHandler;
            }
        };
    }
}
