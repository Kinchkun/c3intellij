package org.c3lang.intellij.debugger;

import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcessConfigurator;
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriver;
import org.jetbrains.annotations.NotNull;

/**
 * Loads the bundled {@code c3.py} LLDB formatters into every native debug session,
 * so C3 {@code String} values render as readable text in Variables/Watches.
 */
public final class C3DebugProcessConfigurator implements CidrDebugProcessConfigurator
{
	private static final Logger LOG = Logger.getInstance(C3DebugProcessConfigurator.class);

	@Override
	public void configure(@NotNull CidrDebugProcess process)
	{
		String script = C3LldbFormatters.scriptPath();
		if (script == null) return;

		process.postCommand((CidrDebugProcess.VoidDebuggerCommand) driver -> {
			if (driver instanceof LLDBDriver lldb)
			{
				try
				{
					lldb.executeInterpreterCommand("command script import \"" + script + "\"");
				}
				catch (Exception e)
				{
					LOG.warn("Failed to load C3 LLDB formatters", e);
				}
			}
		});
	}
}
