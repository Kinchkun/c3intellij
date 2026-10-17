package org.c3lang.intellij.targets;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.c3lang.intellij.C3TestCase;
import org.c3lang.intellij.targets.C3TargetEntry.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class C3TargetsModelTest extends C3TestCase
{
	public void testTreeListsTargetsTestsAndLibraries() throws IOException
	{
		// Manifests are read from disk, so the workspace must be a real directory, not the in-memory fixture.
		Path ws = Files.createTempDirectory("c3-targets");
		write(ws, "project.json", """
			{ "targets": {
			    "tool": { "type": "executable", "sources": [ "apps/tool/src/**" ], "test-sources": [ "apps/tool/test/**" ] },
			    "core": { "type": "static-lib", "sources": [ "core/**" ] } } }
			""");
		write(ws, "lib/argparse.c3l/manifest.json", "{}");
		write(ws, "lib/argparse.c3l/test/a_test.c3", "module a_test @test;");
		write(ws, "lib/empty.c3l/manifest.json", "{}");
		write(ws, "lib/not_a_library/x.c3", "module x;");
		VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ws);
		VfsUtil.markDirtyAndRefresh(false, true, true, dir);

		ProjectEntry project = C3TargetsModel.buildProject(dir);

		List<C3TargetEntry> children = project.children();
		assertEquals(3, children.size());
		TargetEntry tool = (TargetEntry) children.get(0);
		assertEquals("tool", tool.target().name());
		assertEquals(List.of(new TargetTestsEntry(dir, "tool")), tool.children());
		assertEquals(List.of(), ((TargetEntry) children.get(1)).children());

		LibrariesEntry libraries = (LibrariesEntry) children.get(2);
		assertEquals(2, libraries.children().size());
		LibraryEntry argparse = (LibraryEntry) libraries.children().get(0);
		assertEquals("argparse.c3l", argparse.dir().getName());
		assertEquals(1, argparse.children().size());
		assertEquals(List.of(), ((LibraryEntry) libraries.children().get(1)).children());
	}

	/** The panel builds its tree, toolbar and listeners without errors (headless). */
	public void testPanelCreatesAndDisposes()
	{
		C3TargetsPanel panel = new C3TargetsPanel(getProject());
		try
		{
			panel.refresh();
			assertNotNull(panel.getToolbar());
		}
		finally
		{
			Disposer.dispose(panel);
		}
	}

	private static void write(Path root, String relative, String content) throws IOException
	{
		Path file = root.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}
}
