package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3AccessIdent;
import org.c3lang.intellij.psi.C3Arg;
import org.c3lang.intellij.psi.C3ArgList;
import org.c3lang.intellij.psi.C3BinaryExpr;
import org.c3lang.intellij.psi.C3BinaryOp;
import org.c3lang.intellij.psi.C3CallExpr;
import org.c3lang.intellij.psi.C3CallExprTail;
import org.c3lang.intellij.psi.C3CatchUnwrap;
import org.c3lang.intellij.psi.C3CtDefinedExpr;
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
import org.c3lang.intellij.psi.C3MacroFuncBody;
import org.c3lang.intellij.psi.C3MacroImpliesBody;
import org.c3lang.intellij.psi.C3OptionalType;
import org.c3lang.intellij.psi.C3ParamDecl;
import org.c3lang.intellij.psi.C3Parameter;
import org.c3lang.intellij.psi.C3ParameterList;
import org.c3lang.intellij.psi.C3PathIdent;
import org.c3lang.intellij.psi.C3PathIdentExpr;
import org.c3lang.intellij.psi.C3ReturnStmt;
import org.c3lang.intellij.psi.C3StructMemberDeclaration;
import org.c3lang.intellij.psi.C3TernaryExpr;
import org.c3lang.intellij.psi.C3TryUnwrap;
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
 * {@code return} in a function whose return type has no {@code ?} ({@code return NOT_FOUND~;} in
 * a {@code fn void}).
 *
 * <p>A value counts as optional when it is a call whose function or macro declares a {@code T?}
 * return type and that is not unwrapped by a {@code !} or {@code !!} tail, a call to a function
 * with an optional argument (the compiler makes such a call's result optional, whatever the
 * parameter type), a call to a macro without a written return type whose body returns an optional
 * or rethrows, a variable declared {@code T?}, or a fault raised with {@code ~}. Anything else, in particular an unresolved
 * callee, a {@code ??} default or an expression nested in an operator, stays quiet, so the
 * inspection never reports what it does not know.</p>
 *
 * <p>Three more places are reported: an optional argument for a typed macro parameter (the compiler
 * rejects it; untyped, {@code #} and {@code $} parameters accept optionals), a call statement
 * whose optional result is discarded, unless the callee is {@code @maydiscard}, and a rethrow
 * ({@code expr!}) in a function, macro or lambda whose written return type has no {@code ?}.</p>
 *
 * <p>The <i>Propagate the fault</i> quick fix appends {@code !} to the expression and, when the
 * enclosing function, macro or lambda declares a return type without {@code ?}, turns it into
 * {@code T?}. On a {@code return} only the return type changes and the fix is called
 * <i>Change return type to 'T?'</i>.</p>
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
			public void visitTernaryExpr(@NotNull C3TernaryExpr ternary)
			{
				// The grammar reads `target = flag ? a : b` as `(target = flag) ? a : b`: the
				// assigned value is the choice between the two branches.
				List<C3Expr> operands = ternary.getExprList();
				if (operands.size() != 3 || !(operands.get(0) instanceof C3BinaryExpr assignment)) return;
				if (!isPlainAssignment(assignment)) return;
				String declared = nonOptionalDeclaredType(assignment.getLeft());
				if (declared == null) return;
				// Each optional branch is reported on its own, so the fix unwraps exactly that branch.
				reportAssignment(holder, operands.get(1), declared);
				reportAssignment(holder, operands.get(2), declared);
			}

			@Override
			public void visitExprStmt(@NotNull C3ExprStmt statement)
			{
				if (!(statement.getExpr() instanceof C3CallExpr call) || !isInvocation(call)) return;
				PsiElement callee = C3Callees.resolveWithAtMacros(call);
				if (callee == null || C3Callees.isMayDiscard(callee)) return;
				String optional = optionalTypeOf(call);
				if (optional == null) return;
				C3OptionalType returnType = C3Callees.returnType(callee);
				String message = returnType != null && isOptional(returnType)
					? "The call returns '" + optional + "', which is optional and must be handled"
					: callee instanceof C3MacroDefinition
						? "The macro returns an optional, which must be handled"
						: "The result of this call is optional due to its argument(s) and cannot be discarded";
				holder.registerProblem(call, message, ProblemHighlightType.GENERIC_ERROR, new PropagateFaultFix(false, null));
			}

			@Override
			public void visitCallExpr(@NotNull C3CallExpr call)
			{
				if (isRethrow(call))
				{
					reportRethrow(holder, call);
					return;
				}
				if (!isInvocation(call)) return;
				if (!(C3Callees.resolveWithAtMacros(call) instanceof C3MacroDefinition macro)) return;
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
						new PropagateFaultFix(false, null));
				}
			}

			@Override
			public void visitReturnStmt(@NotNull C3ReturnStmt statement)
			{
				C3Expr value = statement.getExpr();
				if (value == null) return;
				C3OptionalType returnType = enclosingReturnType(statement);
				if (returnType == null || isOptional(returnType)) return;
				String optional = optionalTypeOf(value, returnType.getText());
				if (optional == null) return;
				holder.registerProblem(
					value,
					"Optional '" + optional + "' cannot be returned from a function returning '" + returnType.getText() + "'",
					ProblemHighlightType.GENERIC_ERROR,
					new PropagateFaultFix(true, returnType.getText() + "?"));
			}
		};
	}

	/**
	 * A rethrow ({@code expr!}) returns the fault from the enclosing function, macro or lambda, so
	 * that one must return an optional; a macro or lambda that infers its return type is fine.
	 */
	private static void reportRethrow(@NotNull ProblemsHolder holder, @NotNull C3CallExpr rethrow)
	{
		// `$defined(maybe()!)` only checks the expression, nothing is rethrown.
		if (PsiTreeUtil.getParentOfType(rethrow, C3CtDefinedExpr.class) != null) return;
		C3OptionalType returnType = enclosingReturnType(rethrow);
		if (returnType == null || isOptional(returnType)) return;
		holder.registerProblem(
			rethrow,
			"This expression rethrows with '!', but the " + enclosingCallableKind(rethrow) + " returns '"
				+ returnType.getText() + "', which is not optional",
			ProblemHighlightType.GENERIC_ERROR,
			new PropagateFaultFix(true, returnType.getText() + "?"),
			new ForceUnwrapFix());
	}

	private static @NotNull String enclosingCallableKind(@NotNull PsiElement element)
	{
		PsiElement callable = PsiTreeUtil.getParentOfType(element,
			C3FuncDefinition.class, C3MacroDefinition.class, C3LambdaDeclExpr.class, C3LambdaDeclShortExpr.class);
		if (callable instanceof C3MacroDefinition) return "macro";
		return callable instanceof C3FuncDefinition ? "function" : "lambda";
	}

	/** {@code expr!}: a call expression whose tail is the rethrow operator. */
	private static boolean isRethrow(@NotNull C3CallExpr call)
	{
		C3CallExprTail tail = call.getCallExprTail();
		return tail != null && tail.getNode().findChildByType(C3Types.BANG) != null;
	}

	private static void reportAssignment(@NotNull ProblemsHolder holder, @NotNull C3Expr value, @NotNull String declared)
	{
		String optional = optionalTypeOf(value, declared);
		if (optional == null) return;
		holder.registerProblem(
			value,
			"Optional '" + optional + "' cannot be assigned to non-optional '" + declared + "'",
			ProblemHighlightType.GENERIC_ERROR,
			new PropagateFaultFix(false, null));
	}

	/**
	 * As {@link #optionalTypeOf(C3Expr)}, for a value flowing into a place of type {@code target}:
	 * a raised fault ({@code NOT_FOUND~}) has no type of its own and takes the optional of the
	 * target, as in the compiler's message ("cast from 'void?' to 'void'").
	 */
	private static @Nullable String optionalTypeOf(@NotNull C3Expr expr, @NotNull String target)
	{
		String optional = optionalTypeOf(expr);
		return RAISED_FAULT.equals(optional) ? target + "?" : optional;
	}

	/** What {@link #optionalTypeOf(C3Expr)} answers for a raised fault, which has no value type of its own. */
	private static final String RAISED_FAULT = "fault?";

	/** A fault turned into an optional with the {@code ~} suffix: {@code NOT_FOUND~}, {@code io::EOF~}. */
	private static boolean isRaisedFault(@NotNull C3Expr expr)
	{
		if (!(expr instanceof C3CallExpr call)) return false;
		C3CallExprTail tail = call.getCallExprTail();
		return tail != null && tail.getNode().findChildByType(C3Types.BIT_NOT) != null;
	}

	/**
	 * The type text of {@code expr} when it is an optional call, an optional variable, a raised
	 * fault, or a choice with an optional branch ({@code a ?: FAULT~}, {@code flag ? a : FAULT~},
	 * {@code maybe() ?? FAULT~}), else null.
	 */
	static @Nullable String optionalTypeOf(@NotNull C3Expr expr)
	{
		if (isRaisedFault(expr)) return RAISED_FAULT;
		if (expr instanceof C3TernaryExpr ternary)
		{
			List<C3Expr> operands = ternary.getExprList();
			return operands.size() == 3 ? optionalTypeOfAny(operands.get(1), operands.get(2)) : null;
		}
		if (expr instanceof C3BinaryExpr binary)
		{
			C3Expr right = binary.getRight();
			if (right == null) return null;
			// `a ?: b` is `a` or `b`; `a ?? b` handles the optional of `a` and leaves that of `b`.
			if (binary.getNode().findChildByType(C3Types.ELVIS) != null) return optionalTypeOfAny(binary.getLeft(), right);
			if (binary.getNode().findChildByType(C3Types.OPTELSE) != null) return optionalTypeOf(right);
			return null;
		}
		if (expr instanceof C3CallExpr call)
		{
			if (!isInvocation(call)) return null;
			PsiElement callee = C3Callees.resolveWithAtMacros(call);
			C3OptionalType returnType = C3Callees.returnType(callee);
			if (returnType != null && isOptional(returnType)) return returnType.getText();
			// `macro @ok(#call) { ... return CURL_ERROR~; ... }`: the inferred return type is optional.
			if (returnType == null && callee instanceof C3MacroDefinition macro && infersOptional(macro)) return RAISED_FAULT;
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
			if (declared == null || !isOptional(declared) || isCheckedByCatchOrTry(expr)) return null;
			return declared.getText();
		}
		return null;
	}

	/**
	 * Whether a macro without a written return type returns an optional: one of its {@code return}s
	 * (or its {@code => expr} body) is optional, or its body rethrows with {@code !}. Returns and
	 * rethrows of a lambda inside the macro belong to the lambda.
	 */
	private static boolean infersOptional(@NotNull C3MacroDefinition macro)
	{
		return CachedValuesManager.getCachedValue(macro, () -> CachedValueProvider.Result.create(
			Boolean.TRUE.equals(RecursionManager.doPreventingRecursion(macro, false, () -> computeInfersOptional(macro))),
			PsiModificationTracker.MODIFICATION_COUNT));
	}

	private static boolean computeInfersOptional(@NotNull C3MacroDefinition macro)
	{
		C3MacroFuncBody body = macro.getMacroFuncBody();
		C3MacroImpliesBody impliesBody = body.getMacroImpliesBody();
		C3Expr shortBody = body.getExpr() != null ? body.getExpr() : impliesBody != null ? impliesBody.getExpr() : null;
		if (shortBody != null && optionalTypeOf(shortBody) != null) return true;

		for (C3ReturnStmt statement : PsiTreeUtil.findChildrenOfType(body, C3ReturnStmt.class))
		{
			C3Expr value = statement.getExpr();
			if (value != null && belongsTo(statement, macro) && optionalTypeOf(value) != null) return true;
		}
		for (C3CallExpr call : PsiTreeUtil.findChildrenOfType(body, C3CallExpr.class))
		{
			if (isRethrow(call) && belongsTo(call, macro)
				&& PsiTreeUtil.getParentOfType(call, C3CtDefinedExpr.class) == null) return true;
		}
		return false;
	}

	/** Whether {@code element} is in the body of {@code macro} itself and not in a lambda nested in it. */
	private static boolean belongsTo(@NotNull PsiElement element, @NotNull C3MacroDefinition macro)
	{
		return PsiTreeUtil.getParentOfType(element,
			C3FuncDefinition.class, C3MacroDefinition.class, C3LambdaDeclExpr.class, C3LambdaDeclShortExpr.class) == macro;
	}

	/**
	 * Whether the enclosing function, macro or lambda tests the variable {@code identifierExpr}
	 * names with {@code catch} or {@code try} ({@code if (catch prec) return ...;},
	 * {@code if (try value)}). After such a test the compiler treats the variable as unwrapped;
	 * where exactly is flow analysis this inspection does not do, so a tested variable stays quiet.
	 */
	private static boolean isCheckedByCatchOrTry(@NotNull C3Expr identifierExpr)
	{
		PsiElement scope = PsiTreeUtil.getParentOfType(identifierExpr,
			C3FuncDefinition.class, C3MacroDefinition.class, C3LambdaDeclExpr.class, C3LambdaDeclShortExpr.class);
		if (scope == null) return false;
		String name = identifierExpr.getText();
		for (PsiElement unwrap : PsiTreeUtil.findChildrenOfAnyType(scope, C3CatchUnwrap.class, C3TryUnwrap.class))
		{
			for (C3PathIdent tested : PsiTreeUtil.findChildrenOfType(unwrap, C3PathIdent.class))
			{
				if (name.equals(tested.getText())) return true;
			}
		}
		return false;
	}

	/** The optional type of the first optional operand, preferring one with a value type over a bare raised fault. */
	private static @Nullable String optionalTypeOfAny(@NotNull C3Expr first, @NotNull C3Expr second)
	{
		String firstType = optionalTypeOf(first);
		String secondType = optionalTypeOf(second);
		if (firstType != null && !RAISED_FAULT.equals(firstType)) return firstType;
		return secondType != null ? secondType : firstType;
	}

	/**
	 * The written type of the variable or struct member {@code target} names when that type has no
	 * {@code ?}, else null. A struct member ({@code self.curl}) is never optional.
	 */
	private static @Nullable String nonOptionalDeclaredType(@NotNull C3Expr target)
	{
		if (target instanceof C3CallExpr access) return memberType(access);
		if (!(target instanceof C3PathIdentExpr)) return null;
		PsiElement variable = resolveVariable(target);
		if (variable instanceof C3Parameter parameter)
		{
			return parameter.getType() != null ? parameter.getType().getText() : null; // parameters are never optional
		}
		C3OptionalType declared = declaredType(variable);
		return declared != null && !isOptional(declared) ? declared.getText() : null;
	}

	/** The written type of the struct member a member access ({@code self.curl}) resolves to, else null. */
	private static @Nullable String memberType(@NotNull C3CallExpr access)
	{
		C3CallExprTail tail = access.getCallExprTail();
		C3AccessIdent member = tail != null ? tail.getAccessIdent() : null;
		PsiReference reference = member != null ? member.getReference() : null;
		PsiElement resolved = reference != null ? reference.resolve() : null;
		if (!(resolved instanceof C3StructMemberDeclaration declaration)) return null;
		return declaration.getType() != null ? declaration.getType().getText() : null;
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

	/** Replaces the rethrow {@code !} of the reported expression with {@code !!}, which panics instead of returning the fault. */
	private static final class ForceUnwrapFix implements LocalQuickFix
	{
		@Override
		public @NotNull String getFamilyName()
		{
			return "Replace '!' with '!!'";
		}

		@Override
		public void applyFix(@NotNull com.intellij.openapi.project.Project project, @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor)
		{
			if (!(descriptor.getPsiElement() instanceof C3CallExpr rethrow)) return;
			C3CallExprTail tail = rethrow.getCallExprTail();
			com.intellij.lang.ASTNode bang = tail != null ? tail.getNode().findChildByType(C3Types.BANG) : null;
			com.intellij.openapi.editor.Document document =
				com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(rethrow.getContainingFile());
			if (bang == null || document == null) return;
			document.insertString(bang.getTextRange().getEndOffset(), "!");
			com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document);
		}
	}

	/**
	 * Appends {@code !} to the reported expression and makes the enclosing callable return
	 * {@code T?}. On a {@code return} only the return type changes, and the fix is named after it
	 * ("Change return type to 'void?'").
	 */
	private static final class PropagateFaultFix implements LocalQuickFix
	{
		private final boolean returnStatement;
		/** The return type the fix on a {@code return} produces, for its name; null otherwise. */
		private final @Nullable String optionalReturnType;

		private PropagateFaultFix(boolean returnStatement, @Nullable String optionalReturnType)
		{
			this.returnStatement = returnStatement;
			this.optionalReturnType = optionalReturnType;
		}

		@Override
		public @NotNull String getName()
		{
			return optionalReturnType != null ? "Change return type to '" + optionalReturnType + "'" : getFamilyName();
		}

		@Override
		public @NotNull String getFamilyName()
		{
			return returnStatement ? "Change return type to optional" : "Propagate the fault";
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
