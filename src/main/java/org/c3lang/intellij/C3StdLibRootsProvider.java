package org.c3lang.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class C3StdLibRootsProvider extends AdditionalLibraryRootsProvider
{
	@Override public @NotNull Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project)
	{
		var settings = C3SettingsState.getInstance();
		var stdLibPath = settings.stdlibRoot();

		VirtualFile stdLibRoot = LocalFileSystem.getInstance().findFileByPath(stdLibPath);
		if (stdLibRoot != null)
		{
			// Only index C3 sources. If the configured path is broad (e.g. a c3c source checkout root
			// rather than lib/std), this keeps the compiler's C/C++/docs/build files out of the index
			// and avoids the slowdown they cause.
			SyntheticLibrary library = SyntheticLibrary.newImmutableLibrary(
				List.of(stdLibRoot),
				Collections.emptySet(),
				file -> !file.isDirectory() && !"c3".equals(file.getExtension()));
			return Collections.singletonList(library);
		}
		return Collections.emptyList();
	}
}
