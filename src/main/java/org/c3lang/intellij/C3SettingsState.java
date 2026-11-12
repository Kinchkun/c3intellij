package org.c3lang.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

@State(name = "org.intellij.sdk.settings.AppSettingsState", storages = @Storage("C3SettingsPlugin.xml"))
public class C3SettingsState implements PersistentStateComponent<C3SettingsState>
{
    public String sdk = "c3c";
    public String stdlibPath = "";
    /**
     * The {@link #sdk} setting {@link #stdlibPath} was detected from (see {@link C3StdlibLocator}),
     * or empty when the path was entered by hand. A detected path is re-detected when the compiler
     * setting changes; a hand-entered one is kept.
     */
    public String stdlibDetectedFor = "";
    /** Whether tests ({@code c3c test} / {@code compile-test}) are built with AddressSanitizer. */
    public boolean testWithAsan = false;

    public static C3SettingsState getInstance()
    {
        return ApplicationManager.getApplication().getService(C3SettingsState.class);
    }

    /**
     * The directory to index as the standard library. A compiler checkout or install root
     * (containing {@code lib/std}) is narrowed to {@code lib/std}, so the compiler's test suite and
     * examples (e.g. the many {@code module foo;} fixtures) are not offered as importable modules;
     * a library root as the compiler names it (containing {@code std}, e.g. Homebrew's
     * {@code lib/c3}) is narrowed to {@code std}.
     */
    public @NotNull String stdlibRoot()
    {
        return stdlibRoot(stdlibPath);
    }

    /** {@link #stdlibRoot()} for any configured path. */
    public static @NotNull String stdlibRoot(@Nullable String path)
    {
        if (path == null || path.isBlank()) return "";
        File libStd = new File(path, "lib/std");
        if (libStd.isDirectory()) return libStd.getPath();
        File std = new File(path, "std");
        if (std.isDirectory()) return std.getPath();
        return path;
    }

    /**
     * The standard library root of {@code project}: its own
     * {@link C3ProjectSettingsState#stdlibPath override} when set, else the application setting.
     */
    public static @NotNull String stdlibRootFor(@NotNull Project project)
    {
        String override = C3ProjectSettingsState.getInstance(project).stdlibPath;
        if (override != null && !override.isBlank()) return stdlibRoot(override);
        return getInstance().stdlibRoot();
    }

    /**
     * Whether the standard library should be (re)detected: nothing is configured, the configured
     * directory no longer exists (a package manager replaced the compiler install), or the path was
     * detected for another compiler setting than the current one.
     */
    public boolean needsStdlibDetection()
    {
        if (stdlibPath == null || stdlibPath.isBlank()) return true;
        if (!new File(stdlibPath).isDirectory()) return true;
        return stdlibDetectedFor != null && !stdlibDetectedFor.isEmpty() && !stdlibDetectedFor.equals(sdk);
    }

    /** Stores a path found by {@link C3StdlibLocator} for the current compiler setting. */
    public void setDetectedStdlib(@NotNull String path)
    {
        stdlibPath = path;
        stdlibDetectedFor = sdk == null ? "" : sdk;
    }

    /** Stores a path the user entered; it is then never replaced by detection while it exists. */
    public void setManualStdlib(@NotNull String path)
    {
        stdlibPath = path;
        stdlibDetectedFor = "";
    }

    /**
     * Adds {@code --sanitize=address} to the arguments of a test build when {@link #testWithAsan}
     * is enabled and {@code args} does not already pick a sanitizer.
     */
    public void addTestSanitizer(@NotNull List<String> args)
    {
        if (!testWithAsan) return;
        for (String arg : args) if (arg.startsWith("--sanitize")) return;
        args.add("--sanitize=address");
    }

    public C3SettingsState getState()
    {
        return this;
    }

    @Override public void loadState(@NotNull C3SettingsState state)
    {
        XmlSerializerUtil.copyBean(state, this);
    }

}
