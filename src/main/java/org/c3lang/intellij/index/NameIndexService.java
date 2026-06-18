package org.c3lang.intellij.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.c3lang.intellij.psi.C3BaseType;
import org.c3lang.intellij.psi.C3FullyQualifiedNamePsiElement;
import org.c3lang.intellij.psi.C3PsiElement;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class NameIndexService
{
    public static final NameIndexService INSTANCE = new NameIndexService();

    private NameIndexService()
    {
    }

    @NotNull
    public Collection<C3FullyQualifiedNamePsiElement> findByNameEndsWith(@NotNull String name, @NotNull Project project)
    {
        List<C3FullyQualifiedNamePsiElement> result = new ArrayList<>();
        for (String key : StubIndex.getInstance().getAllKeys(NameIndex.KEY, project))
        {
            if (key.endsWith(name))
            {
                for (C3PsiElement element : getElementsByName(key, project))
                {
                    if (element instanceof C3FullyQualifiedNamePsiElement named)
                    {
                        result.add(named);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns every indexed element whose fully-qualified name part (the segment after the
     * module, e.g. {@code HttpClient.init}) starts with {@code namePrefix}. Used to enumerate
     * the methods declared on a type (prefix {@code "<Type>."}).
     */
    @NotNull
    public Collection<C3FullyQualifiedNamePsiElement> findByNamePrefix(@NotNull String namePrefix, @NotNull Project project)
    {
        List<C3FullyQualifiedNamePsiElement> result = new ArrayList<>();
        for (String key : StubIndex.getInstance().getAllKeys(NameIndex.KEY, project))
        {
            if (FullyQualifiedName.parse(key).getName().startsWith(namePrefix))
            {
                for (C3PsiElement element : getElementsByName(key, project))
                {
                    if (element instanceof C3FullyQualifiedNamePsiElement named)
                    {
                        result.add(named);
                    }
                }
            }
        }
        return result;
    }

    @NotNull
    public Collection<C3FullyQualifiedNamePsiElement> findType(@NotNull C3BaseType type, @NotNull Project project)
    {
        List<C3FullyQualifiedNamePsiElement> result = new ArrayList<>();
        for (String key : StubIndex.getInstance().getAllKeys(NameIndex.KEY, project))
        {
            if (!keyMatchesType(key, type)) continue;
            for (C3PsiElement element : getElementsByName(key, project))
            {
                if (element instanceof C3FullyQualifiedNamePsiElement named)
                {
                    result.add(named);
                }
            }
        }
        return result;
    }

    /**
     * Whether a NameIndex {@code key} (a fully-qualified {@code module::Name}) denotes the given
     * type reference.
     *
     * <p>A path-qualified reference ({@code curl::CurlOption}) is matched against the key suffix.
     * An unqualified reference ({@code CurlOption}) is matched on the simple name across <em>all</em>
     * modules — module visibility is intentionally left to the caller's import check, so that a
     * type imported from another module still becomes a resolution candidate.</p>
     */
    private static boolean keyMatchesType(@NotNull String key, @NotNull C3BaseType type)
    {
        if (type.getPath() != null)
        {
            return key.endsWith(type.getText());
        }
        String simpleName = type.getNameIdent() != null ? type.getNameIdent() : type.getText();
        return key.equals(simpleName) || key.endsWith("::" + simpleName);
    }

    @NotNull
    private Collection<C3PsiElement> getElementsByName(@NotNull String string, @NotNull Project project)
    {
        try
        {
            return StubIndex.getElements(
                NameIndex.KEY,
                string,
                project,
                GlobalSearchScope.allScope(project),
                C3PsiElement.class
            );
        }
        catch (com.intellij.openapi.progress.ProcessCanceledException e)
        {
            throw e;
        }
        catch (Throwable t)
        {
            // A stale or inconsistent stub index (e.g. a generated library file under build/
            // that got re-indexed as plain text) can make the platform throw here. Degrade
            // gracefully instead of failing the whole lookup/inspection. Invalidate Caches
            // & Restart resolves the underlying inconsistency.
            LOG.warn("Skipping C3 name index entry '" + string + "' due to an index inconsistency", t);
            return java.util.List.of();
        }
    }

    private static final com.intellij.openapi.diagnostic.Logger LOG =
        com.intellij.openapi.diagnostic.Logger.getInstance(NameIndexService.class);
}
