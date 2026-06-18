package org.c3lang.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Exposes the project's C3 library dependencies — bundled as {@code .c3l} archives, e.g.
 * {@code lib/curl.c3l} — to indexing. A {@code .c3l} is a ZIP containing a {@code .c3i} interface
 * file; mounting its archive root lets that interface be parsed and stub-indexed, so the symbols it
 * declares (types, functions, constants) resolve, autocomplete and support Find Usages — just like
 * the standard library exposed by {@link C3StdLibRootsProvider}.
 */
public final class C3ProjectLibrariesRootsProvider extends AdditionalLibraryRootsProvider
{
	/** Directories below the project root are scanned this deep for {@code .c3l} files. */
	private static final int MAX_DEPTH = 8;

	@Override
	public @NotNull Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project)
	{
		List<VirtualFile> archiveRoots = new ArrayList<>();
		for (VirtualFile archive : findLibraryArchives(project))
		{
			VirtualFile root = archiveRoot(archive);
			if (root != null) archiveRoots.add(root);
		}
		return archiveRoots.isEmpty()
			? List.of()
			: List.of(SyntheticLibrary.newImmutableLibrary(archiveRoots));
	}

	@Override
	public @NotNull Collection<VirtualFile> getRootsToWatch(@NotNull Project project)
	{
		// Watch the local archives so re-indexing is triggered when a dependency changes.
		return findLibraryArchives(project);
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
