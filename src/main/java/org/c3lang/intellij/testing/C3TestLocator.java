package org.c3lang.intellij.testing;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.c3lang.intellij.index.NameIndexService;
import org.c3lang.intellij.psi.C3FullyQualifiedNamePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves test tree nodes to sources: {@code c3test://module::fn} to the test function and
 * {@code c3suite://module} to the file declaring the module's first test.
 */
public final class C3TestLocator implements SMTestLocator
{
	public static final C3TestLocator INSTANCE = new C3TestLocator();
	public static final String TEST_PROTOCOL = "c3test";
	public static final String SUITE_PROTOCOL = "c3suite";

	private C3TestLocator()
	{
	}

	@Override
	public @NotNull List<Location> getLocation(@NotNull String protocol, @NotNull String path,
	                                           @NotNull Project project, @NotNull GlobalSearchScope scope)
	{
		if (DumbService.isDumb(project)) return List.of();

		List<Location> result = new ArrayList<>();
		if (TEST_PROTOCOL.equals(protocol))
		{
			PsiElement test = findTest(project, path);
			if (test != null) result.add(PsiLocation.fromPsiElement(test));
		}
		else if (SUITE_PROTOCOL.equals(protocol))
		{
			PsiElement anyTest = findInModule(project, path);
			if (anyTest != null && anyTest.getContainingFile() != null)
			{
				result.add(PsiLocation.fromPsiElement(anyTest.getContainingFile()));
			}
		}
		return result;
	}

	/** The declaration of {@code module::fn}. */
	static @Nullable PsiElement findTest(@NotNull Project project, @NotNull String name)
	{
		for (C3FullyQualifiedNamePsiElement element :
			NameIndexService.INSTANCE.findByNameEndsWith("::" + C3TestOutputParser.functionOf(name), project))
		{
			if (name.equals(element.getFqName().getFullName())) return element;
		}
		return null;
	}

	/** Some declaration of {@code module}, to open its file. */
	private static @Nullable PsiElement findInModule(@NotNull Project project, @NotNull String module)
	{
		for (C3FullyQualifiedNamePsiElement element : NameIndexService.INSTANCE.findByNamePrefix("", project))
		{
			if (element.getModuleName() != null && module.equals(element.getModuleName().getValue())) return element;
		}
		return null;
	}
}
