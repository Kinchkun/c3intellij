package org.c3lang.intellij.index;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.c3lang.intellij.psi.C3AliasTypeDecl;
import org.c3lang.intellij.psi.C3PsiElement;
import org.c3lang.intellij.psi.C3Type;
import org.c3lang.intellij.psi.C3TypeName;
import org.c3lang.intellij.psi.C3TypedefType;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.c3lang.intellij.stubs.C3TypeEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Follows type aliases: for {@code alias HttpHeaders = HashMap { String, String };} the type
 * {@code HttpHeaders} is {@code HashMap}, so the fields and methods of a {@code HttpHeaders} value
 * are those of {@code HashMap}. (An alias is another name for the same type; a {@code typedef} is
 * a type of its own and is not followed.)
 */
public final class C3TypeAliases
{
	/** Aliases of aliases are followed this far; a cycle (which the compiler rejects) ends here too. */
	private static final int MAX_DEPTH = 8;

	private C3TypeAliases()
	{
	}

	/** The type {@code type} stands for after following its aliases; {@code type} itself when it is no alias. */
	public static @NotNull FullyQualifiedName resolve(@NotNull FullyQualifiedName type, @NotNull Project project)
	{
		if (DumbService.isDumb(project)) return type;

		FullyQualifiedName current = type;
		for (int depth = 0; depth < MAX_DEPTH; depth++)
		{
			FullyQualifiedName aliased = aliasedType(current, project);
			if (aliased == null || aliased.equals(current)) return current;
			current = aliased;
		}
		return current;
	}

	/** The type on the right of {@code alias Name = Type;} when {@code type} is declared that way, else null. */
	private static @Nullable FullyQualifiedName aliasedType(@NotNull FullyQualifiedName type, @NotNull Project project)
	{
		for (C3PsiElement element : StubIndex.getElements(
			TypeIndex.KEY, type.getFullName(), project, GlobalSearchScope.allScope(project), C3PsiElement.class))
		{
			// Structs, enums and the like are told apart by the stub; only the rest needs its syntax tree.
			if (!(element instanceof C3TypeName name) || name.getTypeEnum() != C3TypeEnum.FALLBACK) continue;
			ASTNode parent = name.getNode().getTreeParent();
			if (parent == null || !(parent.getPsi() instanceof C3AliasTypeDecl alias)) continue;

			C3TypedefType target = alias.getTypedefType();
			C3Type aliased = target != null ? target.getType() : null;
			if (aliased == null) return null; // an alias of a function type: `alias Callback = fn void();`
			return name.getModuleDefinition().findDeclaringModule(FullyQualifiedName.Companion.from(aliased));
		}
		return null;
	}
}
