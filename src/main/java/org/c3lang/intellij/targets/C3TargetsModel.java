package org.c3lang.intellij.targets;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.c3lang.intellij.C3ProjectManifest;
import org.c3lang.intellij.C3Projects;
import org.c3lang.intellij.C3TargetRunner;
import org.c3lang.intellij.targets.C3TargetEntry.*;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the rows of the C3 Targets tool window: per C3 project its {@code project.json} targets
 * (with their tests) and its directory libraries (with theirs). Uses only the VFS and the manifests.
 */
public final class C3TargetsModel
{
	private C3TargetsModel()
	{
	}

	public static @NotNull List<ProjectEntry> build(@NotNull Project project)
	{
		List<ProjectEntry> result = new ArrayList<>();
		for (VirtualFile dir : C3Projects.findAll(project)) result.add(buildProject(dir));
		return result;
	}

	static @NotNull ProjectEntry buildProject(@NotNull VirtualFile dir)
	{
		List<C3TargetEntry> children = new ArrayList<>();
		for (C3ProjectManifest.Target target : C3ProjectManifest.targets(dir.getPath()))
		{
			List<C3TargetEntry> targetChildren = new ArrayList<>();
			if (!target.testSources().isEmpty()) targetChildren.add(new TargetTestsEntry(dir, target.name()));
			children.add(new TargetEntry(dir, target, targetChildren));
		}

		List<C3TargetEntry> libraries = new ArrayList<>();
		for (VirtualFile library : libraries(dir))
		{
			List<C3TargetEntry> libraryChildren = new ArrayList<>();
			if (!C3TargetRunner.libraryTestFiles(library).isEmpty()) libraryChildren.add(new LibraryTestsEntry(library));
			libraries.add(new LibraryEntry(library, libraryChildren));
		}
		if (!libraries.isEmpty()) children.add(new LibrariesEntry(libraries));

		return new ProjectEntry(dir, children);
	}

	/**
	 * The directory libraries ({@code <name>.c3l} folders with a {@code manifest.json}) found in the
	 * project's {@code "dependency-search-paths"} (default {@code lib}) that lie inside the project.
	 */
	private static @NotNull List<VirtualFile> libraries(@NotNull VirtualFile dir)
	{
		Set<VirtualFile> searchDirs = new LinkedHashSet<>();
		for (String searchPath : C3ProjectManifest.dependencySearchPaths(dir.getPath()))
		{
			Path resolved = Path.of(dir.getPath()).resolve(searchPath).normalize();
			if (!resolved.startsWith(Path.of(dir.getPath()))) continue;
			VirtualFile searchDir = dir.getFileSystem().findFileByPath(resolved.toString().replace('\\', '/'));
			if (searchDir != null && searchDir.isDirectory()) searchDirs.add(searchDir);
		}

		List<VirtualFile> result = new ArrayList<>();
		for (VirtualFile searchDir : searchDirs)
		{
			for (VirtualFile child : searchDir.getChildren())
			{
				if (child.isDirectory() && child.getName().endsWith(".c3l") && child.findChild("manifest.json") != null)
				{
					result.add(child);
				}
			}
		}
		result.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
		return result;
	}
}
