package org.c3lang.intellij;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns {@code <file>.c3:<line>} references in run/test console output (e.g. the
 * {@code Test failed ^^^ ( httpclient_test.c3:38 )} line emitted by {@code c3c test})
 * into clickable hyperlinks that jump to the failing assertion.
 */
public final class C3ConsoleFilter implements Filter
{
    private static final Pattern PATTERN = Pattern.compile("([\\w.\\-/\\\\]+\\.c3[ti]?):(\\d+)");

    private final Project project;

    public C3ConsoleFilter(@NotNull Project project)
    {
        this.project = project;
    }

    @Override
    public @Nullable Result applyFilter(@NotNull String line, int entireLength)
    {
        Matcher matcher = PATTERN.matcher(line);
        List<ResultItem> items = new ArrayList<>();
        int lineStart = entireLength - line.length();

        while (matcher.find())
        {
            VirtualFile file = findFile(matcher.group(1));
            if (file == null) continue;

            int lineNumber = parseLine(matcher.group(2));
            items.add(new ResultItem(
                lineStart + matcher.start(),
                lineStart + matcher.end(),
                new OpenFileHyperlinkInfo(project, file, Math.max(0, lineNumber - 1))
            ));
        }

        return items.isEmpty() ? null : new Result(items);
    }

    private @Nullable VirtualFile findFile(@NotNull String path)
    {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.isEmpty() || project.isDisposed() || DumbService.isDumb(project)) return null;

        return ReadAction.compute(() -> {
            Collection<VirtualFile> files =
                FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.allScope(project));
            return files.isEmpty() ? null : files.iterator().next();
        });
    }

    private static int parseLine(@NotNull String value)
    {
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }
}
