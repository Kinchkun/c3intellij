package org.c3lang.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exposes the project's C3 library dependencies — bundled as {@code .c3l} archives, e.g.
 * {@code lib/curl.c3l} — to indexing. A {@code .c3l} is a ZIP containing a {@code .c3i} interface
 * file; mounting its archive root lets that interface be parsed and stub-indexed, so the symbols it
 * declares (types, functions, constants) resolve, autocomplete and support Find Usages — just like
 * the standard library exposed by {@link C3StdLibRootsProvider}.
 *
 * <p>Also exposes the libraries {@code project.json} declares under {@code "dependencies"}, found
 * via {@code "dependency-search-paths"} — including {@code .c3l} directories outside the project.</p>
 */
public final class C3ProjectLibrariesRootsProvider extends AdditionalLibraryRootsProvider
{
	/** The dependency libraries last seen in {@code project.json}; see {@link C3ProjectManifestListener}. */
	static final Key<List<Path>> DEPENDENCIES_KEY = Key.create("c3.project.dependencyLibraries");

	/** Directories below the project root are scanned this deep for {@code .c3l} files. */
	private static final int MAX_DEPTH = 8;

	@Override
	public @NotNull Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project)
	{
		Set<VirtualFile> roots = new LinkedHashSet<>();
		for (VirtualFile archive : findLibraryArchives(project))
		{
			VirtualFile root = archiveRoot(archive);
			if (root != null) roots.add(root);
		}
		for (VirtualFile library : findDependencyLibraries(project))
		{
			VirtualFile root = library.isDirectory() ? library : archiveRoot(library);
			if (root != null) roots.add(root);
		}
		return roots.isEmpty()
			? List.of()
			: List.of(SyntheticLibrary.newImmutableLibrary(List.copyOf(roots)));
	}

	@Override
	public @NotNull Collection<VirtualFile> getRootsToWatch(@NotNull Project project)
	{
		// Watch the local archives and dependency directories so re-indexing is triggered when a
		// dependency changes.
		Set<VirtualFile> watched = new LinkedHashSet<>(findLibraryArchives(project));
		watched.addAll(findDependencyLibraries(project));
		return watched;
	}

	/**
	 * The {@code .c3l} libraries (archives or directories) that the workspace's C3 projects depend on
	 * in their {@code project.json} — e.g. {@code ../mystd/mystd.c3l} via
	 * {@code "dependency-search-paths": ["../mystd"]} — so a sibling library repository is indexed and
	 * its declarations resolve. Every C3 project of the IDE project counts (see {@link C3Projects}), so
	 * this also works for a workspace holding several of them. Library directories inside the IDE
	 * project are skipped: they are already indexed as project content.
	 */
	private static @NotNull List<VirtualFile> findDependencyLibraries(@NotNull Project project)
	{
		String basePath = project.getBasePath();
		if (basePath == null) return List.of();

		List<Path> dependencies = dependencyPaths(project);
		project.putUserData(DEPENDENCIES_KEY, dependencies);

		Path base = Path.of(basePath).normalize();
		List<VirtualFile> libraries = new ArrayList<>();
		for (Path library : dependencies)
		{
			if (Files.isDirectory(library) && library.startsWith(base)) continue;
			VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(library);
			if (file != null) libraries.add(file);
		}
		return libraries;
	}

	/** The dependency libraries declared by all C3 projects of {@code project}, without duplicates. */
	static @NotNull List<Path> dependencyPaths(@NotNull Project project)
	{
		Set<Path> paths = new LinkedHashSet<>();
		for (VirtualFile dir : C3Projects.findAll(project))
		{
			paths.addAll(C3ProjectManifest.dependencyLibraries(dir.getPath()));
		}
		return List.copyOf(paths);
	}

	private static @NotNull List<VirtualFile> findLibraryArchives(@NotNull Project project)
	{
		String basePath = project.getBasePath();
		if (basePath == null) return List.of();
		VirtualFile base = LocalFileSystem.getInstance().findFileByPath(basePath);
		if (base == null) return List.of();

		List<VirtualFile> archives = new ArrayList<>();
		collect(base, archives, 0);
		return archives;
	}

	private static void collect(@NotNull VirtualFile dir, @NotNull List<VirtualFile> archives, int depth)
	{
		if (depth > MAX_DEPTH) return;
		for (VirtualFile child : dir.getChildren())
		{
			if (child.isDirectory())
			{
				String name = child.getName();
				if (name.equals("build") || name.startsWith(".")) continue; // skip output / hidden dirs
				collect(child, archives, depth + 1);
			}
			else if ("c3l".equals(child.getExtension()))
			{
				archives.add(child);
			}
		}
	}

	private static @Nullable VirtualFile archiveRoot(@NotNull VirtualFile archive)
	{
		VirtualFile root = JarFileSystem.getInstance().getJarRootForLocalFile(archive);
		if (root != null) return root;
		// `.c3l` is not a registered archive extension, so force the ZIP mount by path.
		return JarFileSystem.getInstance().findFileByPath(archive.getPath() + JarFileSystem.JAR_SEPARATOR);
	}
}
