package org.c3lang.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@State(name = "org.intellij.sdk.settings.AppSettingsState", storages = @Storage("C3SettingsPlugin.xml"))
public class C3SettingsState implements PersistentStateComponent<C3SettingsState>
{
    public String sdk = "c3c";
    public String stdlibPath = "";

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

    public C3SettingsState getState()
    {
        return this;
    }

    @Override public void loadState(@NotNull C3SettingsState state)
    {
        XmlSerializerUtil.copyBean(state, this);
    }

}