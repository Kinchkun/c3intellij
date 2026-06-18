package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3ConstDeclarationStmt;
import org.c3lang.intellij.psi.C3FaultDefinition;
import org.c3lang.intellij.psi.C3FaultdefDecl;
import org.c3lang.intellij.psi.C3GlobalDecl;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3LocalDeclarationStmt;
import org.c3lang.intellij.psi.C3ParamDecl;
import org.c3lang.intellij.psi.C3Parameter;
import org.c3lang.intellij.psi.C3TopLevel;
import org.c3lang.intellij.psi.C3Types;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Deletes an unused declaration flagged by {@link C3UnusedDeclarationInspection}. The problem is
 * registered on the declaration's name, so this walks up to the element that should actually be
 * removed. Declarations that share a statement with siblings ({@code faultdef A, B}, {@code int a,
 * b}, parameter lists) have just their own entry plus a separating comma removed; everything else is
 * removed as a whole statement.
 */
public final class DeleteUnusedDeclarationFix implements LocalQuickFix
{
	private final String description;

	public DeleteUnusedDeclarationFix(@NotNull String description)
	{
		this.description = description;
	}

	@Override
	public @NotNull String getName()
	{
		return "Delete " + description;
	}

	@Override
	public @NotNull String getFamilyName()
	{
		return "Delete unused declaration";
	}

	@Override
	public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
	{
		PsiElement element = descriptor.getPsiElement();
		if (element == null) return;

		C3Parameter parameter = PsiTreeUtil.getParentOfType(element, C3Parameter.class, false);
		if (parameter != null)
		{
			C3ParamDecl decl = PsiTreeUtil.getParentOfType(parameter, C3ParamDecl.class, false);
			deleteListItem(decl != null ? decl : parameter);
			return;
		}

		C3FaultDefinition fault = PsiTreeUtil.getParentOfType(element, C3FaultDefinition.class, false);
		if (fault != null)
		{
			C3FaultdefDecl faultdef = PsiTreeUtil.getParentOfType(fault, C3FaultdefDecl.class, false);
			if (faultdef != null && faultdef.getFaultDefinitionList().size() > 1)
			{
				deleteListItem(fault);
				return;
			}
			deleteStatement(faultdef != null ? faultdef : fault);
			return;
		}

		C3GlobalDecl global = PsiTreeUtil.getParentOfType(element, C3GlobalDecl.class, false);
		if (global != null && element.getNode().getElementType() == C3Types.IDENT)
		{
			if (nameCount(global) > 1)
			{
				deleteListItem(element);
				return;
			}
			deleteStatement(global);
			return;
		}

		C3ConstDeclarationStmt constStmt =
			PsiTreeUtil.getParentOfType(element, C3ConstDeclarationStmt.class, false);
		if (constStmt != null)
		{
			deleteStatement(constStmt);
			return;
		}

		C3LocalDeclAfterType local = PsiTreeUtil.getParentOfType(element, C3LocalDeclAfterType.class, false);
		if (local != null)
		{
			C3LocalDeclarationStmt stmt =
				PsiTreeUtil.getParentOfType(local, C3LocalDeclarationStmt.class, false);
			if (stmt != null && stmt.getDeclStmtAfterType().getLocalDeclAfterTypeList().size() > 1)
			{
				deleteListItem(local);
				return;
			}
			deleteStatement(stmt != null ? stmt : local);
			return;
		}

		C3TopLevel topLevel = PsiTreeUtil.getParentOfType(element, C3TopLevel.class, false);
		if (topLevel != null) topLevel.delete();
	}

	/** Deletes a statement, preferring to remove the enclosing top-level wrapper if there is one. */
	private static void deleteStatement(@NotNull PsiElement statement)
	{
		PsiElement parent = statement.getParent();
		(parent instanceof C3TopLevel ? parent : statement).delete();
	}

	private static int nameCount(@NotNull C3GlobalDecl global)
	{
		int count = 0;
		for (PsiElement child = global.getFirstChild(); child != null; child = child.getNextSibling())
		{
			if (child.getNode().getElementType() == C3Types.IDENT) count++;
		}
		return count;
	}

	/** Removes a comma-list entry along with the comma that separates it from its neighbour. */
	private static void deleteListItem(@NotNull PsiElement item)
	{
		PsiElement comma = adjacentComma(item);
		if (comma != null) comma.delete();
		item.delete();
	}

	private static @Nullable PsiElement adjacentComma(@NotNull PsiElement element)
	{
		for (PsiElement next = element.getNextSibling(); next != null; next = next.getNextSibling())
		{
			if (next instanceof PsiWhiteSpace) continue;
			return ",".equals(next.getText()) ? next : null;
		}
		for (PsiElement prev = element.getPrevSibling(); prev != null; prev = prev.getPrevSibling())
		{
			if (prev instanceof PsiWhiteSpace) continue;
			return ",".equals(prev.getText()) ? prev : null;
		}
		return null;
	}
}
