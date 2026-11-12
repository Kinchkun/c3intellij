package org.c3lang.intellij;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/** Settings of one project, stored in {@code .idea/c3.xml} so they travel with the project. */
@State(name = "C3ProjectSettings", storages = @Storage("c3.xml"))
public final class C3ProjectSettingsState implements PersistentStateComponent<C3ProjectSettingsState>
{
	/**
	 * A standard library for this project only, like {@code c3c --stdlib}. Empty means the
	 * application-level {@link C3SettingsState#stdlibPath} is used.
	 */
	public String stdlibPath = "";

	public static @NotNull C3ProjectSettingsState getInstance(@NotNull Project project)
	{
		return project.getService(C3ProjectSettingsState.class);
	}

	@Override public C3ProjectSettingsState getState()
	{
		return this;
	}

	@Override public void loadState(@NotNull C3ProjectSettingsState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
