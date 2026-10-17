package org.c3lang.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.index.NameIndexService;
import org.c3lang.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class C3ModuleDefinitionMixinImpl extends C3PsiElementImpl implements C3ModuleDefinition
{
	public C3ModuleDefinitionMixinImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	@Override
	public @NotNull List<ModuleName> getImports()
	{
		return ModuleName.Companion.getImportList(this);
	}

	@Override
	public @NotNull List<C3ImportDecl> getImportDeclarations()
	{
		List<C3TopLevel> topLevels = PsiTreeUtil.getChildrenOfTypeAsList(this, C3TopLevel.class);
		List<C3ImportDecl> result = new ArrayList<>();
		for (C3TopLevel topLevel : topLevels)
		{
			C3ImportDecl importDecl = topLevel.getImportDecl();
			if (importDecl != null) result.add(importDecl);
		}
		return result;
	}

	@Override
	public @Nullable ModuleName getModuleName()
	{
		return ModuleName.Companion.from(this);
	}

	@Override
	public @NotNull List<C3ImportPath> getImportPaths()
	{
		List<C3ImportPath> result = new ArrayList<>();
		for (C3ImportDecl decl : getImportDeclarations())
		{
			result.addAll(decl.getImportPaths().getImportPathList());
		}
		return result;
	}

	@Override
	public boolean containsImportOrSameModule(@NotNull C3FullyQualifiedNamePsiElement callable)
	{
		if (Objects.equals(callable.getModuleName(), getModuleName()))
		{
			return true;
		}
		return ModuleName.autoImportedPrefix(callable.getModuleName()) != null
			|| getImportedModuleCovering(callable.getModuleName()) != null;
	}

	@Override
	public boolean contains(@NotNull C3PathIdent pathIdent)
	{
		return !getImportOf(pathIdent).isEmpty();
	}

	@Override
	public boolean contains(@NotNull C3Path path)
	{
		C3PathIdent pathIdentParent = PsiTreeUtil.getParentOfType(path, C3PathIdent.class);
		return pathIdentParent != null && contains(pathIdentParent);
	}

	@Override
	public @NotNull List<C3ImportPath> getImportOf(@NotNull C3PathIdent pathIdent)
	{
		return getImportOf(pathIdent.getText());
	}

	@Override
	public @NotNull List<C3ImportPath> getImportOf(@NotNull C3PathIdentExpr pathIdentExpr)
	{
		return getImportOf(pathIdentExpr.getText());
	}

	private @NotNull List<C3ImportPath> getImportOf(@NotNull String text)
	{
		List<String> moduleValues = new ArrayList<>();
		for (C3FullyQualifiedNamePsiElement element :
			NameIndexService.INSTANCE.findByNameEndsWith(text, getProject()))
		{
			if (element.getFqName().getSuffixName().equals(text))
			{
				ModuleName moduleName = element.getModuleName();
				if (moduleName != null) moduleValues.add(moduleName.getValue());
			}
		}

		List<C3ImportPath> result = new ArrayList<>();
		for (C3ImportPath importPath : getImportPaths())
		{
			ModuleName mn = importPath.getModuleName();
				if (mn != null)
				{
					for (String moduleValue : moduleValues)
					{
						if (mn.covers(new ModuleName(moduleValue)))
						{
							result.add(importPath);
							break;
						}
					}
				}
		}
		return result;
	}

	@Override
	public @NotNull List<FullyQualifiedName> resolve(@NotNull C3PathIdentExpr expr)
	{
		String nameIdent = expr.getText();
		if (expr.getPathIdent().getPath() == null)
		{
			return Collections.singletonList(new FullyQualifiedName(null, nameIdent));
		}

		List<FullyQualifiedName> result = new ArrayList<>();
		for (C3ImportPath importPath : getImportOf(expr))
		{
			result.add(new FullyQualifiedName(importPath.getModuleName(), nameIdent));
		}
		return result;
	}

	/**
	 * Resolves a type reference to its fully-qualified candidates using only this file's text:
	 * primitives have no module, an unqualified name belongs to this module, and a path-qualified
	 * name ({@code io::File}) belongs to whichever visible module (this one or an import) ends
	 * with that path.
	 *
	 * <p>This deliberately does not consult the stub index. It runs while stubs are built
	 * ({@link StructField}, {@code C3StructMemberDeclarationStub}), where index access throws
	 * {@code IndexNotReadyException} during indexing and would make the stub content depend on
	 * which other files happen to be indexed already.</p>
	 */
	@Override
	public @NotNull List<FullyQualifiedName> resolve(@NotNull C3Type type)
	{
		C3BaseType baseType = type.getBaseType();
		if (baseType.isPrimitiveType())
		{
			return Collections.singletonList(new FullyQualifiedName(null, baseType.getText()));
		}

		C3Path path = baseType.getPath();
		if (path == null)
		{
			return Collections.singletonList(new FullyQualifiedName(getModuleName(), baseType.getText()));
		}

		String modulePath = stripTrailingScope(path.getText());
		String name = baseType.getText()
			.substring(path.getStartOffsetInParent() + path.getTextLength())
			.trim();

		Set<ModuleName> visibleModules = new LinkedHashSet<>();
		ModuleName ownModule = getModuleName();
		if (ownModule != null) visibleModules.add(ownModule);
		for (C3ImportPath importPath : getImportPaths())
		{
			ModuleName mn = importPath.getModuleName();
			if (mn != null) visibleModules.add(mn);
		}

		List<FullyQualifiedName> result = new ArrayList<>();
		for (ModuleName module : visibleModules)
		{
			if (endsWithPath(module, modulePath))
			{
				result.add(new FullyQualifiedName(module, name));
			}
		}
		return result;
	}

	private static boolean endsWithPath(@NotNull ModuleName module, @NotNull String path)
	{
		String value = module.getValue();
		return value.equals(path) || value.endsWith("::" + path);
	}

	private static @NotNull String stripTrailingScope(@NotNull String pathText)
	{
		String text = pathText.trim();
		return text.endsWith("::") ? text.substring(0, text.length() - 2).trim() : text;
	}

	@Override
	public @NotNull List<C3ImportPath> getImportPaths(@NotNull ModuleName moduleName)
	{
		List<C3ImportPath> result = new ArrayList<>();
		for (C3ImportPath importPath : getImportPaths())
		{
			ModuleName mn = importPath.getModuleName();
			if (mn != null && mn.getValue().equals(moduleName.getValue())) result.add(importPath);
		}
		return result;
	}
}
