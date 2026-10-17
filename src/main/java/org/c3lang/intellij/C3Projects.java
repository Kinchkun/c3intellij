package org.c3lang.intellij;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Locates the C3 projects (directories containing a {@code project.json}) of an IDE project. The IDE
 * project may be a single C3 project or a workspace holding several of them, e.g. a monorepo with
 * {@code c3script/project.json} and {@code mystd/project.json} but none at its root.
 *
 * <p>Uses only the VFS, never the file index, so it is safe to call while indexing (e.g. from an
 * {@code AdditionalLibraryRootsProvider}).</p>
 */
public final class C3Projects
{
	public static final String MANIFEST = "project.json";

	/** Directories below a content root are scanned this deep for {@code project.json}. */
	private static final int MAX_DEPTH = 4;

	private C3Projects()
	{
	}

	/** The directory of the C3 project owning {@code file}: the nearest ancestor with a {@code project.json}. */
	public static @Nullable VirtualFile findProjectDir(@Nullable VirtualFile file)
	{
		for (VirtualFile dir = file == null ? null : file.isDirectory() ? file : file.getParent();
		     dir != null;
		     dir = dir.getParent())
		{
			VirtualFile manifest = dir.findChild(MANIFEST);
			if (manifest != null && !manifest.isDirectory()) return dir;
		}
		return null;
	}

	/**
	 * The directory library owning {@code file}: the nearest ancestor named {@code <name>.c3l} that
	 * holds a {@code manifest.json}, e.g. {@code lib/argparse.c3l} for {@code lib/argparse.c3l/test/a.c3}.
	 */
	public static @Nullable VirtualFile findLibraryDir(@Nullable VirtualFile file)
	{
		for (VirtualFile dir = file == null ? null : file.isDirectory() ? file : file.getParent();
		     dir != null;
		     dir = dir.getParent())
		{
			if (!dir.getName().endsWith(".c3l")) continue;
			VirtualFile manifest = dir.findChild("manifest.json");
			if (manifest != null && !manifest.isDirectory()) return dir;
		}
		return null;
	}

	/** The directories of all C3 projects in the IDE project's content roots. */
	public static @NotNull List<VirtualFile> findAll(@NotNull Project project)
	{
		Set<VirtualFile> roots = new LinkedHashSet<>();
		VirtualFile base = baseDir(project);
		if (base != null) roots.add(base);
		if (!project.isDefault())
		{
			for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots())
			{
				if (base == null || !VfsUtilCore.isAncestor(base, root, false)) roots.add(root);
			}
		}

		List<VirtualFile> result = new ArrayList<>();
		for (VirtualFile root : roots) collect(root, result, 0);
		return result;
	}

	/**
	 * The default working directory for a new C3 run configuration: the C3 project of the file open
	 * in the editor, else the only C3 project of the workspace, else the IDE project's base dir.
	 */
	public static @Nullable String defaultWorkingDirectory(@NotNull Project project)
	{
		if (!project.isDefault())
		{
			for (VirtualFile file : FileEditorManager.getInstance(project).getSelectedFiles())
			{
				VirtualFile dir = findProjectDir(file);
				if (dir != null) return dir.getPath();
			}
		}

		List<VirtualFile> all = findAll(project);
		if (all.size() == 1) return all.getFirst().getPath();
		return project.getBasePath();
	}

	/**
	 * A short label for the C3 project in {@code dir}, used to tell run configurations apart in a
	 * workspace; null when {@code dir} is the IDE project's base dir (a single-project setup).
	 */
	public static @Nullable String label(@NotNull Project project, @Nullable VirtualFile dir)
	{
		if (dir == null || dir.equals(baseDir(project))) return null;
		return dir.getName();
	}

	private static @Nullable VirtualFile baseDir(@NotNull Project project)
	{
		String basePath = project.getBasePath();
		return basePath != null ? LocalFileSystem.getInstance().findFileByPath(basePath) : null;
	}

	private static void collect(@NotNull VirtualFile dir, @NotNull List<VirtualFile> result, int depth)
	{
		if (!dir.isDirectory() || depth > MAX_DEPTH) return;

		VirtualFile manifest = dir.findChild(MANIFEST);
		if (manifest != null && !manifest.isDirectory())
		{
			result.add(dir);
			return; // A C3 project doesn't nest other C3 projects.
		}

		for (VirtualFile child : dir.getChildren())
		{
			if (!child.isDirectory()) continue;
			String name = child.getName();
			if (name.startsWith(".") || name.equals("build") || name.equals("node_modules")) continue;
			if (name.endsWith(".c3l")) continue; // A library, not a project.
			collect(child, result, depth + 1);
		}
	}
}
