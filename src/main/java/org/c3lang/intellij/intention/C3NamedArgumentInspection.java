package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3Arg;
import org.c3lang.intellij.psi.C3CallInvocation;
import org.c3lang.intellij.psi.C3CallablePsiElement;
import org.c3lang.intellij.psi.C3Calls;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3MacroDefinition;
import org.c3lang.intellij.psi.C3NamedIdent;
import org.c3lang.intellij.psi.ParamType;
import org.c3lang.intellij.psi.C3Visitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Flags named call arguments ({@code my_function(param: 3)}) whose name does not match any parameter
 * of the called function. Only fires when the callee resolves to plain functions — unresolved calls
 * and macros (whose parameters are more flexible) are left alone to avoid false positives.
 */
public final class C3NamedArgumentInspection extends LocalInspectionTool
{
	@Override
	public @NotNull String getDisplayName()
	{
		return "Unknown named argument";
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
			public void visitArg(@NotNull C3Arg arg)
			{
				C3NamedIdent named = arg.getNamedIdent();
				if (named == null) return;

				// Only validate calls; the same `name: value` shape also appears in initializer lists.
				C3CallInvocation invocation = PsiTreeUtil.getParentOfType(arg, C3CallInvocation.class);
				if (invocation == null) return;

				String name = named.getText();
				// Compile-time ($x) / hash (#x) / type ($Type) named idents are not plain arguments.
				if (name.isEmpty() || name.charAt(0) == '$' || name.charAt(0) == '#') return;

				List<C3CallablePsiElement> callables = C3Calls.resolveCallables(invocation);
				if (callables.isEmpty()) return; // unresolved callee — say nothing

				for (C3CallablePsiElement callable : callables)
				{
					// Macros accept flexible parameters; don't second-guess their argument names.
					if (callable instanceof C3MacroDefinition) return;
					for (ParamType parameter : callable.getParameterTypes())
					{
						if (name.equals(parameter.getName())) return; // matches an overload's parameter
					}
				}

				holder.registerProblem(
					named,
					"Cannot resolve parameter '" + name + "'",
					ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
			}
		};
	}
}
