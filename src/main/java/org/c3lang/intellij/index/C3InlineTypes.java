package org.c3lang.intellij.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.c3lang.intellij.psi.C3PsiElement;
import org.c3lang.intellij.psi.C3StructDeclaration;
import org.c3lang.intellij.psi.C3StructMemberDeclaration;
import org.c3lang.intellij.psi.C3Type;
import org.c3lang.intellij.psi.C3TypeName;
import org.c3lang.intellij.psi.C3TypedefDecl;
import org.c3lang.intellij.psi.C3TypedefType;
import org.c3lang.intellij.psi.C3Types;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The types whose methods and fields a type takes over through {@code inline}:
 * {@code typedef TcpSocket = inline Socket;} makes the methods of {@code Socket} callable on a
 * {@code TcpSocket}, and {@code struct Derived { inline Base base; }} does the same for the methods
 * and fields of {@code Base}.
 */
public final class C3InlineTypes
{
	private C3InlineTypes()
	{
	}

	/**
	 * {@code typeFullName} followed by the types it inlines, directly or through further inline
	 * types, closest first. Member lookup walks this list and takes the first type declaring the
	 * member, as c3c does.
	 */
	public static @NotNull List<String> withInlineParents(@NotNull String typeFullName, @NotNull Project project)
	{
		Set<String> types = new LinkedHashSet<>();
		types.add(typeFullName);
		List<String> pending = new ArrayList<>(List.of(typeFullName));
		while (!pending.isEmpty())
		{
			List<String> next = new ArrayList<>();
			for (String current : pending)
			{
				for (FullyQualifiedName parent : inlineParents(current, project))
				{
					if (types.add(parent.getFullName())) next.add(parent.getFullName());
				}
			}
			pending = next;
		}
		return new ArrayList<>(types);
	}

	/** The types that the declaration of {@code typeFullName} inlines directly. */
	private static @NotNull List<FullyQualifiedName> inlineParents(@NotNull String typeFullName, @NotNull Project project)
	{
		List<FullyQualifiedName> parents = new ArrayList<>();
		for (C3PsiElement element : StubIndex.getElements(
			TypeIndex.KEY, typeFullName, project, GlobalSearchScope.allScope(project), C3PsiElement.class))
		{
			if (!(element instanceof C3TypeName)) continue;
			PsiElement declaration = element.getParent();

			if (declaration instanceof C3TypedefDecl typedef && isInline(typedef))
			{
				C3TypedefType aliased = typedef.getTypedefType();
				addType(parents, element, aliased != null ? aliased.getType() : null);
			}
			else if (declaration instanceof C3StructDeclaration struct && struct.getStructBody() != null)
			{
				for (C3StructMemberDeclaration member : struct.getStructBody().getStructMemberDeclarationList())
				{
					if (isInline(member)) addType(parents, element, member.getType());
				}
			}
		}
		return parents;
	}

	private static boolean isInline(@NotNull PsiElement declaration)
	{
		return declaration.getNode().findChildByType(C3Types.KW_INLINE) != null;
	}

	private static void addType(
		@NotNull List<FullyQualifiedName> parents, @NotNull C3PsiElement declaredIn, @Nullable C3Type type)
	{
		if (type == null) return;
		FullyQualifiedName name =
			declaredIn.getModuleDefinition().resolveDeclaringModule(FullyQualifiedName.Companion.from(type));
		if (name != null) parents.add(name);
	}
}
