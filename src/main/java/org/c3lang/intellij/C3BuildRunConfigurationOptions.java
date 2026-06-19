package org.c3lang.intellij;

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.openapi.components.StoredProperty;


public class C3BuildRunConfigurationOptions extends RunConfigurationOptions {
    private final StoredProperty<String> myWorkingDirectory =
            string("").provideDelegate(this, "workingDirectory");

    private final StoredProperty<String> myTarget =
            string("").provideDelegate(this, "target");

    private final StoredProperty<String> myArgs =
            string("").provideDelegate(this, "args");

    private final StoredProperty<String> myProgramArgs =
            string("").provideDelegate(this, "programArgs");

    public String getTarget()
    {
        return myTarget.getValue(this);
    }

    public void setTarget(String target)
    {
        myTarget.setValue(this, target);
    }

    public String getProgramArgs()
    {
        return myProgramArgs.getValue(this);
    }

    public void setProgramArgs(String programArgs)
    {
        myProgramArgs.setValue(this, programArgs);
    }

    public String getWorkingDirectory()
    {
        return myWorkingDirectory.getValue(this);
    }

    public void setWorkingDirectory(String workingDirectory)
    {
        myWorkingDirectory.setValue(this, workingDirectory);
    }

    public String getArgs()
    {
        return myArgs.getValue(this);
    }

    public void setArgs(String args)
    {
        myArgs.setValue(this, args);
    }
}
