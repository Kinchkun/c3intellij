package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Flags a use of a generic type without its type parameters: {@code List foo;} where {@code List}
 * is declared in a generic module ({@code module std::collections::list <Type>;}) or is a generic
 * struct ({@code struct Container <Type>}). Such a type must be instantiated, {@code List { String }}.
 * Inside the generic module itself the bare name denotes the parametrized type and is fine.
 */
public final class C3MissingGenericParametersInspection extends LocalInspectionTool
{
	@Override
	public @NotNull String getDisplayName()
	{
		return "Missing generic parameters";
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
			public void visitBaseType(@NotNull C3BaseType baseType)
			{
				if (baseType.getGenericParameters() != null || baseType.isPrimitiveType()) return;
				String name = baseType.getNameIdent();
				if (name == null) return;
				PsiReference reference = baseType.getReference();
				PsiElement resolved = reference != null ? reference.resolve() : null;
				if (!(resolved instanceof C3TypeName typeName)) return;
				PsiElement declaration = typeName.getParent();
				boolean genericDeclaration = declaration != null && genericDecl(declaration) != null;
				C3ModuleSection section = PsiTreeUtil.getParentOfType(typeName, C3ModuleSection.class);
				boolean genericModule = section != null && section.getModule().getGenericDecl() != null;
				if (!genericDeclaration && !genericModule) return;
				// Inside the generic module, or the generic declaration itself, the bare name denotes
				// the parametrized type; so does the receiver type of a method definition.
				if (genericModule && baseType.getModuleDefinition().isSameModule(typeName)) return;
				if (genericDeclaration && PsiTreeUtil.isAncestor(declaration, baseType, false)) return;
				if (PsiTreeUtil.getParentOfType(baseType, C3FuncName.class) != null) return;
				holder.registerProblem(
					baseType,
					"Generic type '" + name + "' needs its type parameters: " + name + " { ... }",
					ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
			}
		};
	}

	/** The type parameters declared on a type declaration ({@code struct Container <Type>}), if any. */
	private static C3GenericDecl genericDecl(@NotNull PsiElement declaration)
	{
		return switch (declaration)
		{
			case C3StructDeclaration d -> d.getGenericDecl();
			case C3EnumDeclaration d -> d.getGenericDecl();
			case C3BitstructDeclaration d -> d.getGenericDecl();
			case C3TypedefDecl d -> d.getGenericDecl();
			case C3AliasTypeDecl d -> d.getGenericDecl();
			default -> null;
		};
	}
}
