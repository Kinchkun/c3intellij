package org.c3lang.intellij.targets;

import com.intellij.openapi.vfs.VirtualFile;
import org.c3lang.intellij.C3ProjectManifest;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** A row of the C3 Targets tool window. */
public sealed interface C3TargetEntry
{
	/** A C3 project: a directory holding a {@code project.json}. */
	record ProjectEntry(@NotNull VirtualFile dir, @NotNull List<C3TargetEntry> children) implements C3TargetEntry {}

	/** A target of {@code project.json}; executables can be run. */
	record TargetEntry(@NotNull VirtualFile projectDir, @NotNull C3ProjectManifest.Target target,
	                   @NotNull List<C3TargetEntry> children) implements C3TargetEntry {}

	/** The tests of a target ({@code c3c test <target>}). */
	record TargetTestsEntry(@NotNull VirtualFile projectDir, @NotNull String target) implements C3TargetEntry {}

	/** The directory libraries ({@code lib/<name>.c3l}) of a project. */
	record LibrariesEntry(@NotNull List<C3TargetEntry> children) implements C3TargetEntry {}

	/** A directory library. */
	record LibraryEntry(@NotNull VirtualFile dir, @NotNull List<C3TargetEntry> children) implements C3TargetEntry {}

	/** The tests in a library's {@code test/} folder ({@code c3c compile-test --lib <name> ...}). */
	record LibraryTestsEntry(@NotNull VirtualFile library) implements C3TargetEntry {}
}
