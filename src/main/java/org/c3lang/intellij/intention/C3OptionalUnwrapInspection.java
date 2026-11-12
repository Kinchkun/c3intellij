package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3Arg;
import org.c3lang.intellij.psi.C3ArgList;
import org.c3lang.intellij.psi.C3BinaryExpr;
import org.c3lang.intellij.psi.C3BinaryOp;
import org.c3lang.intellij.psi.C3CallExpr;
import org.c3lang.intellij.psi.C3CallExprTail;
import org.c3lang.intellij.psi.C3Expr;
import org.c3lang.intellij.psi.C3ExprStmt;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3FuncDefinition;
import org.c3lang.intellij.psi.C3GlobalDecl;
import org.c3lang.intellij.psi.C3GlobalName;
import org.c3lang.intellij.psi.C3LambdaDeclExpr;
import org.c3lang.intellij.psi.C3LambdaDeclShortExpr;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3MacroDefinition;
import org.c3lang.intellij.psi.C3OptionalType;
import org.c3lang.intellij.psi.C3ParamDecl;
import org.c3lang.intellij.psi.C3Parameter;
import org.c3lang.intellij.psi.C3ParameterList;
import org.c3lang.intellij.psi.C3PathIdent;
import org.c3lang.intellij.psi.C3PathIdentExpr;
import org.c3lang.intellij.psi.C3ReturnStmt;
import org.c3lang.intellij.psi.C3Types;
import org.c3lang.intellij.psi.C3Visitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reports an optional value ({@code T?}) flowing into a place that holds a plain {@code T}, which
 * the compiler rejects ("It is not possible to cast from 'sz?' to 'sz'"): the initializer of a
 * local declared without {@code ?}, an assignment to such a local, global or parameter, and a
 * {@code return} in a function whose return type has no {@code ?}.
 *
 * <p>A value counts as optional when it is a call whose function or macro declares a {@code T?}
 * return type and that is not unwrapped by a {@code !} or {@code !!} tail, a call to a function
 * with an optional argument (the compiler makes such a call's result optional, whatever the
 * parameter type), or a variable declared {@code T?}. Anything else, in particular an unresolved
 * callee, a {@code ??} default or an expression nested in an operator, stays quiet, so the
 * inspection never reports what it does not know.</p>
 *
 * <p>Two more places are reported: an optional argument for a typed macro parameter (the compiler
 * rejects it; untyped, {@code #} and {@code $} parameters accept optionals), and a call statement
 * whose optional result is discarded, unless the callee is {@code @maydiscard}.</p>
 *
 * <p>The <i>Propagate the fault</i> quick fix appends {@code !} to the expression and, when the
 * enclosing function, macro or lambda declares a return type without {@code ?}, turns it into
 * {@code T?}. On a {@code return} only the return type changes.</p>
 */
public final class C3OptionalUnwrapInspection extends LocalInspectionTool
{
	@Override
	public @NotNull String getDisplayName()
	{
		return "Optional value used as non-optional";
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
			public void visitLocalDeclAfterType(@NotNull C3LocalDeclAfterType declaration)
			{
				C3Expr initializer = declaration.getExpr();
				C3OptionalType declared = declaration.getDeclaredType();
				if (initializer == null || declared == null || isOptional(declared)) return;
				reportAssignment(holder, initializer, declared.getText());
			}

			@Override
			public void visitBinaryExpr(@NotNull C3BinaryExpr binary)
			{
				if (!isPlainAssignment(binary)) return;
				C3Expr value = binary.getRight();
				if (value == null) return;
				String declared = nonOptionalDeclaredType(binary.getLeft());
				if (declared == null) return;
				reportAssignment(holder, value, declared);
			}

			@Override
			public void visitExprStmt(@NotNull C3ExprStmt statement)
			{
				if (!(statement.getExpr() instanceof C3CallExpr call) || !isInvocation(call)) return;
				PsiElement callee = C3Callees.resolve(call);
				if (callee == null || C3Callees.isMayDiscard(callee)) return;
				String optional = optionalTypeOf(call);
				if (optional == null) return;
				C3OptionalType returnType = C3Callees.returnType(callee);
				String message = returnType != null && isOptional(returnType)
					? "The call returns '" + optional + "', which is optional and must be handled"
					: "The result of this call is optional due to its argument(s) and cannot be discarded";
				holder.registerProblem(call, message, ProblemHighlightType.GENERIC_ERROR, new PropagateFaultFix(false));
			}

			@Override
			public void visitCallExpr(@NotNull C3CallExpr call)
			{
				if (!isInvocation(call)) return;
				if (!(C3Callees.resolve(call) instanceof C3MacroDefinition macro)) return;
				List<C3Parameter> parameters = typedPositionalParameters(macro);
				int positional = 0;
				for (C3Arg arg : arguments(call))
				{
					C3Expr value = arg.getExpr();
					if (value == null || arg.getParamPath() != null) continue;
					if (arg.getNode().findChildByType(C3Types.ELLIPSIS) != null) break; // a splat ends the mapping
					C3Parameter parameter;
					if (arg.getNamedIdent() != null)
					{
						parameter = byName(parameters, arg.getNamedIdent().getText());
					}
					else
					{
						if (positional >= parameters.size()) break;
						parameter = parameters.get(positional++);
						if (parameter != null && isVararg(parameter)) break;
					}
					if (parameter == null || !isTyped(parameter)) continue;
					String optional = optionalTypeOf(value);
					if (optional == null) continue;
					holder.registerProblem(
						value,
						"Optional '" + optional + "' cannot be passed to parameter '" + parameter.getName()
							+ "' of type '" + parameter.getType().getText() + "'",
						ProblemHighlightType.GENERIC_ERROR,
						new PropagateFaultFix(false));
				}
			}

			@Override
			public void visitReturnStmt(@NotNull C3ReturnStmt statement)
			{
				C3Expr value = statement.getExpr();
				if (value == null) return;
				C3OptionalType returnType = enclosingReturnType(statement);
				if (returnType == null || isOptional(returnType)) return;
				String optional = optionalTypeOf(value);
				if (optional == null) return;
				holder.registerProblem(
					value,
					"Optional '" + optional + "' cannot be returned from a function returning '" + returnType.getText() + "'",
					ProblemHighlightType.GENERIC_ERROR,
					new PropagateFaultFix(true));
			}
		};
	}

	private static void reportAssignment(@NotNull ProblemsHolder holder, @NotNull C3Expr value, @NotNull String declared)
	{
		String optional = optionalTypeOf(value);
		if (optional == null) return;
		holder.registerProblem(
			value,
			"Optional '" + optional + "' cannot be assigned to non-optional '" + declared + "'",
			ProblemHighlightType.GENERIC_ERROR,
			new PropagateFaultFix(false));
	}

	/** The type text of {@code expr} when it is an optional call or an optional variable, else null. */
	static @Nullable String optionalTypeOf(@NotNull C3Expr expr)
	{
		if (expr instanceof C3CallExpr call)
		{
			if (!isInvocation(call)) return null;
			PsiElement callee = C3Callees.resolve(call);
			C3OptionalType returnType = C3Callees.returnType(callee);
			if (returnType != null && isOptional(returnType)) return returnType.getText();
			// `f(maybe())` where f is a function: the compiler makes the call's result optional.
			if (callee instanceof C3FuncDef && hasOptionalArgument(call))
			{
				return (returnType != null ? returnType.getText() : "void") + "?";
			}
			return null;
		}
		if (expr instanceof C3PathIdentExpr)
		{
			C3OptionalType declared = declaredType(resolveVariable(expr));
			return declared != null && isOptional(declared) ? declared.getText() : null;
		}
		return null;
	}

	/** The written type of the variable {@code target} names when that type has no {@code ?}, else null. */
	private static @Nullable String nonOptionalDeclaredType(@NotNull C3Expr target)
	{
		if (!(target instanceof C3PathIdentExpr)) return null;
		PsiElement variable = resolveVariable(target);
		if (variable instanceof C3Parameter parameter)
		{
			return parameter.getType() != null ? parameter.getType().getText() : null; // parameters are never optional
		}
		C3OptionalType declared = declaredType(variable);
		return declared != null && !isOptional(declared) ? declared.getText() : null;
	}

	private static @Nullable PsiElement resolveVariable(@NotNull C3Expr identifierExpr)
	{
		C3PathIdent ident = PsiTreeUtil.findChildOfType(identifierExpr, C3PathIdent.class);
		PsiReference reference = ident != null ? ident.getReference() : null;
		return reference != null ? reference.resolve() : null;
	}

	private static @Nullable C3OptionalType declaredType(@Nullable PsiElement variable)
	{
		if (variable instanceof C3LocalDeclAfterType local) return local.getDeclaredType();
		if (variable instanceof C3GlobalName global && global.getParent() instanceof C3GlobalDecl decl) return decl.getOptionalType();
		return null;
	}

	/** A real `(...)` invocation, not a `!`/`!!`/`++` tail and not a body-macro call with a trailing block. */
	private static boolean isInvocation(@NotNull C3CallExpr call)
	{
		C3CallExprTail tail = call.getCallExprTail();
		return tail != null && tail.getCallInvocation() != null && tail.getCompoundStatement() == null;
	}

	private static @NotNull List<C3Arg> arguments(@NotNull C3CallExpr call)
	{
		C3CallExprTail tail = call.getCallExprTail();
		if (tail == null || tail.getCallInvocation() == null) return List.of();
		C3ArgList args = tail.getCallInvocation().getCallArgList().getArgList();
		return args != null ? args.getArgList() : List.of();
	}

	private static boolean hasOptionalArgument(@NotNull C3CallExpr call)
	{
		for (C3Arg arg : arguments(call))
		{
			C3Expr value = arg.getExpr();
			if (value != null && arg.getParamPath() == null && optionalTypeOf(value) != null) return true;
		}
		return false;
	}

	/** The macro's parameters in call order, without an implicit {@code self} receiver. */
	private static @NotNull List<C3Parameter> typedPositionalParameters(@NotNull C3MacroDefinition macro)
	{
		List<C3Parameter> result = new ArrayList<>();
		C3ParameterList list = macro.getMacroParams().getParameterList();
		if (list == null) return result;
		for (C3ParamDecl decl : list.getParamDeclList()) result.add(decl.getParameter());
		if (!result.isEmpty() && "self".equals(result.get(0).getName())) result.remove(0);
		return result;
	}

	private static @Nullable C3Parameter byName(@NotNull List<C3Parameter> parameters, @NotNull String name)
	{
		for (C3Parameter parameter : parameters) if (name.equals(parameter.getName())) return parameter;
		return null;
	}

	/** A parameter with a written type and a plain name; {@code x}, {@code #expr}, {@code $const} and {@code &self} accept optionals. */
	private static boolean isTyped(@NotNull C3Parameter parameter)
	{
		String name = parameter.getName();
		return parameter.getType() != null && name != null && !name.startsWith("#") && !name.startsWith("$");
	}

	private static boolean isVararg(@NotNull C3Parameter parameter)
	{
		return parameter.getNode().findChildByType(C3Types.ELLIPSIS) != null;
	}

	private static boolean isPlainAssignment(@NotNull C3BinaryExpr binary)
	{
		for (PsiElement child = binary.getFirstChild(); child != null; child = child.getNextSibling())
		{
			if (child instanceof C3BinaryOp op) return op.getText().equals("=");
		}
		return false;
	}

	static boolean isOptional(@NotNull C3OptionalType type)
	{
		return type.getNode().findChildByType(C3Types.QUESTION) != null;
	}

	/**
	 * The written return type of the function, macro or lambda whose body contains {@code element};
	 * null outside any, or for a macro or lambda that infers its type.
	 */
	static @Nullable C3OptionalType enclosingReturnType(@NotNull PsiElement element)
	{
		PsiElement callable = PsiTreeUtil.getParentOfType(element,
			C3FuncDefinition.class, C3MacroDefinition.class, C3LambdaDeclExpr.class, C3LambdaDeclShortExpr.class);
		if (callable instanceof C3FuncDefinition function) return function.getFuncDef().getFuncHeader().getOptionalType();
		if (callable instanceof C3MacroDefinition macro) return macro.getMacroHeader().getOptionalType();
		if (callable instanceof C3LambdaDeclExpr lambda) return lambda.getLambdaDecl().getOptionalType();
		if (callable instanceof C3LambdaDeclShortExpr lambda) return lambda.getLambdaDecl().getOptionalType();
		return null;
	}

	/** Appends {@code !} to the reported expression (not on a {@code return}) and makes the enclosing callable return {@code T?}. */
	private static final class PropagateFaultFix implements LocalQuickFix
	{
		private final boolean returnStatement;

		private PropagateFaultFix(boolean returnStatement)
		{
			this.returnStatement = returnStatement;
		}

		@Override
		public @NotNull String getFamilyName()
		{
			return "Propagate the fault";
		}

		@Override
		public boolean startInWriteAction()
		{
			return true;
		}

		@Override
		public void applyFix(@NotNull com.intellij.openapi.project.Project project, @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor)
		{
			PsiElement expr = descriptor.getPsiElement();
			if (expr == null) return;
			com.intellij.openapi.editor.Document document =
				com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(expr.getContainingFile());
			if (document == null) return;

			C3OptionalType returnType = enclosingReturnType(expr);
			int typeEnd = returnType != null && !isOptional(returnType) ? returnType.getTextRange().getEndOffset() : -1;
			int exprEnd = returnStatement ? -1 : expr.getTextRange().getEndOffset();

			// Later offsets first, so the earlier insertion does not shift them.
			if (exprEnd >= 0 && exprEnd >= typeEnd) document.insertString(exprEnd, "!");
			if (typeEnd >= 0) document.insertString(typeEnd, "?");
			if (exprEnd >= 0 && exprEnd < typeEnd) document.insertString(exprEnd, "!");
			com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document);
		}
	}
}
