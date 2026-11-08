package org.c3lang.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.c3lang.intellij.index.ModuleIndex;
import org.c3lang.intellij.psi.reference.C3ReferenceBase;
import java.util.Collection;
import java.util.Collections;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.c3lang.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public abstract class C3PathMixinImpl extends C3PsiNamedElementImpl implements C3Path
{
	public C3PathMixinImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	@Override
	public @Nullable PsiElement getNameIdentifier()
	{
		return this;
	}

	@Override
	public @Nullable String getName()
	{
		return getText();
	}

	@Override
	public @NotNull PsiElement setName(@NotNull String name)
	{
		// TODO
		return this;
	}

	@Override
	public int getTextOffset()
	{
		return getNode().getStartOffset();
	}

	/** Resolves the module path to the declarations of the visible module it names. */
	@Override
	public @Nullable PsiReference getReference()
	{
		return new C3ModulePathReference(this);
	}

	private static final class C3ModulePathReference extends C3ReferenceBase<C3Path>
	{
		C3ModulePathReference(@NotNull C3Path element)
		{
			super(element);
		}

		@Override
		public @NotNull Collection<C3PsiElement> multiResolve()
		{
			String path = myElement.getText().replaceAll("\\s+", "");
			if (path.endsWith("::")) path = path.substring(0, path.length() - 2);
			if (path.isEmpty()) return Collections.emptyList();
			C3ModuleDefinition context = myElement.getModuleDefinition();
			Project project = myElement.getProject();
			List<C3PsiElement> result = new ArrayList<>();
			for (String key : StubIndex.getInstance().getAllKeys(ModuleIndex.KEY, project))
			{
				if (!key.equals(path) && !key.endsWith("::" + path)) continue;
				ModuleName module = new ModuleName(key);
				if (!context.isSameModule(module) && context.getVisibleModulePrefix(module) == null) continue;
				for (C3PsiElement element : StubIndex.getElements(ModuleIndex.KEY, key, project, GlobalSearchScope.allScope(project), C3PsiElement.class))
				{
					if (element instanceof C3Module) result.add(element);
				}
			}
			return result;
		}

		/** The path without its trailing {@code ::}. */
		@Override
		public @NotNull TextRange getRangeInElement()
		{
			String text = myElement.getText();
			return TextRange.from(0, text.endsWith("::") ? text.length() - 2 : text.length());
		}
	}

	@Override
	public void shorten()
	{
		List<ASTNode> idents = new ArrayList<>(Arrays.asList(
			getNode().getChildren(TokenSet.create(C3Types.IDENT, C3Types.SCOPE))));

		if (idents.size() <= 2)
		{
			// bar::
			return;
		}

		idents.remove(idents.size() - 1); // ::
		idents.remove(idents.size() - 1); // IDENT

		// remove std::
		deleteChildRange(
			idents.get(0).getPsi(),
			idents.get(idents.size() - 1).getPsi()
		);
	}
}
