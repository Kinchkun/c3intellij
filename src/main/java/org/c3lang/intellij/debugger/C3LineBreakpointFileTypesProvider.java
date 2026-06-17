package org.c3lang.intellij.debugger;

import com.intellij.openapi.fileTypes.FileType;
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrLineBreakpointFileTypesProvider;
import org.c3lang.intellij.C3InterfaceFileType;
import org.c3lang.intellij.C3SourceFileType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Tells CLion's native (CIDR/LLDB) debugger that C3 source files support line
 * breakpoints, so they can be set in the gutter and bound via DWARF.
 */
public final class C3LineBreakpointFileTypesProvider implements CidrLineBreakpointFileTypesProvider
{
	@Override
	public @NotNull Set<FileType> getFileTypes()
	{
		return Set.of((FileType) C3SourceFileType.INSTANCE, C3InterfaceFileType.INSTANCE);
	}
}
