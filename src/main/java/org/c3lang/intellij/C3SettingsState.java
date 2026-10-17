package org.c3lang.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

@State(name = "org.intellij.sdk.settings.AppSettingsState", storages = @Storage("C3SettingsPlugin.xml"))
public class C3SettingsState implements PersistentStateComponent<C3SettingsState>
{
    public String sdk = "c3c";
    public String stdlibPath = "";
    /** Whether tests ({@code c3c test} / {@code compile-test}) are built with AddressSanitizer. */
    public boolean testWithAsan = false;

    public static C3SettingsState getInstance()
    {
        return ApplicationManager.getApplication().getService(C3SettingsState.class);
    }

    /**
     * The directory to index as the standard library. A compiler checkout or install root
     * (containing {@code lib/std}) is narrowed to {@code lib/std}, so the compiler's test suite and
     * examples (e.g. the many {@code module foo;} fixtures) are not offered as importable modules.
     */
    public @NotNull String stdlibRoot()
    {
        if (stdlibPath == null || stdlibPath.isBlank()) return "";
        File std = new File(stdlibPath, "lib/std");
        return std.isDirectory() ? std.getPath() : stdlibPath;
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