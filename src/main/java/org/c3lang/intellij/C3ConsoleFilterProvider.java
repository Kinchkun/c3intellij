package org.c3lang.intellij;

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class C3ConsoleFilterProvider implements ConsoleFilterProvider
{
    @Override
    public Filter @NotNull [] getDefaultFilters(@NotNull Project project)
    {
        return new Filter[]{ new C3ConsoleFilter(project) };
    }
}
