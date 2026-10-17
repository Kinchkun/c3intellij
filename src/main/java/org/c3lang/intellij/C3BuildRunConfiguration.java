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
 * A "Run Project" configuration – it knows how to launch {@code c3c run <target>} for the project.
 *
 * <p>This is the representative example of IntelliJ's execution framework, which is made of four
 * cooperating pieces (see also the {@code C3*RunConfiguration*} siblings):
 * <ol>
 *   <li>{@link ConfigurationType} ({@link C3ConfigurationType}) – the family shown in the UI.</li>
 *   <li>{@link ConfigurationFactory} ({@code C3BuildRunConfigurationFactory}) – creates instances of
 *       this class with sensible defaults.</li>
 *   <li>the configuration itself (this class) – holds the user's settings, delegating storage to an
 *       options object ({@code C3CompileRunConfigurationOptions}, persisted to the run config XML).</li>
 *   <li>a {@link SettingsEditor} ({@code C3BuildRunEditor}, returned by {@link #getConfigurationEditor})
 *       – the form the user edits.</li>
 * </ol>
 *
 * <p>When the user presses Run, the platform calls {@link #getState}; we return a
 * {@link CommandLineState} that assembles a {@link GeneralCommandLine} invoking the {@code c3c}
 * compiler and streams its output to the Run console.
 */
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
