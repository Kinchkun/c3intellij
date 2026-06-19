package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3CompoundStatement;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3LocalDeclarationStmt;
import org.c3lang.intellij.psi.C3Visitor;
import org.jetbrains.annotations.NotNull;

/**
 * Flags a local variable that is declared more than once in the same block scope, e.g.
 *
 * <pre>{@code
 * int a = 1;
 * int a = 2;   // error: 'a' is already defined in the scope
 * }</pre>
 *
 * <p>Only declarations sharing the same immediate {@link C3CompoundStatement} are compared, so a
 * variable that shadows one from an enclosing block (a different scope) is not reported.</p>
 */
public final class C3DuplicateVariableInspection extends LocalInspectionTool
{
	@Override
	public @NotNull String getDisplayName()
	{
		return "Duplicate variable declaration";
	}

	@Override
	public @NotNull String getGroupDisplayName()
	{
		return "C3";
	}

	@Override
	public @NotNull PsiElementVisitor buildVisitor(
		@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session)
	{
		if (!(session.getFile() instanceof C3File)) return PsiElementVisitor.EMPTY_VISITOR;

		return new C3Visitor()
		{
			@Override
			public void visitLocalDeclAfterType(@NotNull C3LocalDeclAfterType local)
			{
				// Only plain statement declarations (`int a;`, `Foo x = ...;`); skip for/if-header
				// declarations and compile-time ($x) variables.
				if (PsiTreeUtil.getParentOfType(local, C3LocalDeclarationStmt.class) == null) return;
				String name = local.getNameIdent();
				if (name == null || name.equals("_") || name.startsWith("$")) return;

				C3CompoundStatement scope = PsiTreeUtil.getParentOfType(local, C3CompoundStatement.class);
				if (scope == null) return;

				if (!hasEarlierDeclaration(local, name, scope)) return;

				PsiElement nameElement = local.getNameIdentElement();
				holder.registerProblem(
					nameElement != null ? nameElement : local,
					"Variable '" + name + "' is already defined in the scope",
					ProblemHighlightType.GENERIC_ERROR);
			}

			/** Whether an earlier declaration of {@code name} exists directly in {@code scope}. */
			private boolean hasEarlierDeclaration(
				@NotNull C3LocalDeclAfterType local, @NotNull String name, @NotNull C3CompoundStatement scope)
			{
				int offset = local.getTextOffset();
				for (C3LocalDeclAfterType other : PsiTreeUtil.collectElementsOfType(scope, C3LocalDeclAfterType.class))
				{
					if (other == local || other.getTextOffset() >= offset) continue;
					if (!name.equals(other.getNameIdent())) continue;
					if (PsiTreeUtil.getParentOfType(other, C3LocalDeclarationStmt.class) == null) continue;
					// Same immediate scope only — a declaration in a nested block is a separate scope.
					if (PsiTreeUtil.getParentOfType(other, C3CompoundStatement.class) == scope) return true;
				}
				return false;
			}
		};
	}
}
