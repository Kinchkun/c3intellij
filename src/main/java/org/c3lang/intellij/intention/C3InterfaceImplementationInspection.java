package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3InterfaceImpl;
import org.c3lang.intellij.psi.C3StructDeclaration;
import org.c3lang.intellij.psi.C3Visitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/** Flags structs that declare they implement an interface but are missing its methods. */
public final class C3InterfaceImplementationInspection extends LocalInspectionTool
{
	@Override
	public @NotNull String getDisplayName()
	{
		return "Unimplemented interface methods";
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
			public void visitStructDeclaration(@NotNull C3StructDeclaration struct)
			{
				C3InterfaceImpl impl = struct.getInterfaceImpl();
				if (impl == null) return;

				List<C3FuncDef> missing = C3Interfaces.missingMethods(struct);
				if (missing.isEmpty()) return;

				String methods = missing.stream()
					.map(C3Interfaces::methodSimpleName)
					.collect(Collectors.joining(", "));

				PsiElement problem = impl.getTypeName();
				holder.registerProblem(
					problem,
					"Interface methods not implemented: " + methods,
					ProblemHighlightType.GENERIC_ERROR,
					new ImplementInterfaceMethodsFix()
				);
			}
		};
	}
}
