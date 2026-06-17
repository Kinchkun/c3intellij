package org.c3lang.intellij.debugger;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.jetbrains.cidr.execution.Installer;
import com.jetbrains.cidr.execution.RunParameters;
import com.jetbrains.cidr.execution.TrivialInstaller;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration;
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriverConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Runs the already-built C3 executable under CLion's bundled LLDB. */
public final class C3DebugRunParameters extends RunParameters
{
	private final GeneralCommandLine commandLine;

	public C3DebugRunParameters(@NotNull GeneralCommandLine commandLine)
	{
		this.commandLine = commandLine;
	}

	@Override
	public @NotNull Installer getInstaller()
	{
		return new TrivialInstaller(commandLine);
	}

	@Override
	public @Nullable String getArchitectureId()
	{
		return null;
	}

	@Override
	public @NotNull DebuggerDriverConfiguration getDebuggerDriverConfiguration()
	{
		return new LLDBDriverConfiguration();
	}
}
