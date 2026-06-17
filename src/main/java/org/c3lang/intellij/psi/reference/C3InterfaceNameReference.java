package org.c3lang.intellij.psi.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import org.c3lang.intellij.index.NameIndexService;
import org.c3lang.intellij.psi.C3FullyQualifiedNamePsiElement;
import org.c3lang.intellij.psi.C3InterfaceDefinition;
import org.c3lang.intellij.psi.C3TypeName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the interface name in a {@code struct X (Interface)} implementation clause to its
 * {@code interface} declaration. In the grammar this name is a {@code C3TypeName} (the element
 * used for definitions), so it would otherwise have no reference for navigation/docs/usages.
 */
public final class C3InterfaceNameReference extends PsiReferenceBase<C3TypeName>
{
	public C3InterfaceNameReference(@NotNull C3TypeName element)
	{
		super(element, new TextRange(0, element.getTextLength()));
	}

	@Override
	public @Nullable PsiElement resolve()
	{
		String name = myElement.getName();
		if (name == null) return null;

		for (C3FullyQualifiedNamePsiElement el :
			NameIndexService.INSTANCE.findByNameEndsWith(name, myElement.getProject()))
		{
			if (el instanceof C3TypeName typeName
				&& name.equals(typeName.getName())
				&& typeName.getParent() instanceof C3InterfaceDefinition)
			{
				return typeName;
			}
		}
		return null;
	}
}
