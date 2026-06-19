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

public class C3BuildRunConfiguration extends RunConfigurationBase<C3CompileRunConfigurationOptions>
{
    protected C3BuildRunConfiguration(Project project, ConfigurationFactory factory, String name)
    {
        super(project, factory, name);
    }

    @Override public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor()
    {
        return new C3BuildRunEditor();
    }

    @Override protected @NotNull C3BuildRunConfigurationOptions getOptions()
    {
        return (C3BuildRunConfigurationOptions)super.getOptions();
    }

    public String getWorkingDirectory()
    {
        return getOptions().getWorkingDirectory();
    }

    public void setWorkingDirectory(String workingDirectory)
    {
        getOptions().setWorkingDirectory(workingDirectory);
    }

    public String getTarget()
    {
        return getOptions().getTarget();
    }

    public void setTarget(String target)
    {
        getOptions().setTarget(target);
    }

    public String getArgs()
    {
        return getOptions().getArgs();
    }

    public void setArgs(String args)
    {
        getOptions().setArgs(args);
    }

    public String getProgramArgs()
    {
        return getOptions().getProgramArgs();
    }

    public void setProgramArgs(String programArgs)
    {
        getOptions().setProgramArgs(programArgs);
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
                GeneralCommandLine commandLine = new GeneralCommandLine(sdk, "run");

                // Run a specific executable target, e.g. `c3c run my_target`.
                if (getTarget() != null && !getTarget().isBlank()) commandLine.addParameter(getTarget().trim());

                // I couldn't just add the whole args string here because the GeneralCommandLine class adds quotes
                // around parameters with spaces (so it would look like this: c3c run "--param value" which isn't valid
                // syntax).
                // Instead, I'm splitting the args string by spaces and adding that array.
                if (getArgs() != null && !getArgs().isBlank()) commandLine.addParameters(getArgs().trim().split(" "));

                // Everything after `--` is forwarded to the compiled program:
                // `c3c run <target> <args> -- <program args>`.
                if (getProgramArgs() != null && !getProgramArgs().isBlank())
                {
                    commandLine.addParameter("--");
                    commandLine.addParameters(getProgramArgs().trim().split(" "));
                }

                commandLine.setWorkDirectory(getWorkingDirectory());

                OSProcessHandler processHandler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine);
                ProcessTerminatedListener.attach(processHandler);
                return processHandler;
            }
        };
    }
}
