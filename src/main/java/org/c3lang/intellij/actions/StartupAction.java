package org.c3lang.intellij.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.c3lang.intellij.C3SettingsState;
import org.c3lang.intellij.C3StdlibLocator;
import org.c3lang.intellij.C3StdlibRoots;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;

/**
 * Detects the standard library on project open when {@link C3SettingsState#needsStdlibDetection()
 * it is needed}: nothing configured, the configured directory gone, or the compiler setting changed
 * since the path was detected. Runs on a background thread.
 */
public class StartupAction implements ProjectActivity
{
    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation)
    {
        C3SettingsState settings = C3SettingsState.getInstance();
        if (!settings.needsStdlibDetection()) return Unit.INSTANCE;

        Path detected = C3StdlibLocator.detect(settings.sdk == null ? "" : settings.sdk);
        if (detected == null)
        {
            // A stale path indexes nothing; clearing it brings up the "not set" banner with its
            // Setup and Detect actions.
            if (settings.stdlibPath != null && !settings.stdlibPath.isBlank() && !new File(settings.stdlibPath).isDirectory())
            {
                settings.setManualStdlib("");
                C3StdlibRoots.refresh(project);
            }
            return Unit.INSTANCE;
        }

        String path = detected.toString();
        boolean unchanged = path.equals(settings.stdlibPath);
        settings.setDetectedStdlib(path);
        if (!unchanged) C3StdlibRoots.refresh(project);
        return Unit.INSTANCE;
    }
}
