package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3AssignTypeExpr;
import org.c3lang.intellij.psi.C3BinaryExpr;
import org.c3lang.intellij.psi.C3BinaryOp;
import org.c3lang.intellij.psi.C3CallExpr;
import org.c3lang.intellij.psi.C3CallExprTail;
import org.c3lang.intellij.psi.C3Expr;
import org.c3lang.intellij.psi.C3ExprStmt;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3OptionalType;
import org.c3lang.intellij.psi.C3Types;
import org.c3lang.intellij.psi.C3UnaryExpr;
import org.c3lang.intellij.psi.C3Visitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Warns when a statement evaluates an expression but discards its result — e.g. {@code compute();}
 * where {@code compute} returns a value that is never stored, returned or otherwise used. The
 * <i>Create local variable</i> quick fix captures the value in a new declaration; <i>Discard
 * result with (void)</i> marks the discard as intentional.
 *
 * <p>Statements that exist for their side effect are not reported: assignments, increments
 * ({@code x++}) and explicit {@code (void)} discards. Calls are only reported when the callee
 * resolves to a function, or a macro with a written return type, whose type is not {@code void} or
 * {@code void?} and that is not {@code @maydiscard} — so calling a {@code void} function as a
 * statement stays clean. {@code mayFail()!} and {@code mayFail()!!} look through the unwrap: a
 * {@code void?} callee is pure error handling, a {@code T?} one still produces a {@code T} that is
 * being thrown away.</p>
 */
public final class C3UnusedExpressionResultInspection extends LocalInspectionTool
{
	private static final Set<String> ASSIGNMENT_OPERATORS = Set.of(
		"=", "*=", "/=", "%=", "+=", "-=", ">>=", "<<=", "&=", "^=", "|=");

	@Override
	public @NotNull String getDisplayName()
	{
		return "Unused expression result";
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
			public void visitExprStmt(@NotNull C3ExprStmt statement)
			{
				C3Expr expr = statement.getExpr();
				if (expr == null) return;

				Discarded discarded = classify(expr);
				if (discarded == null) return;

				holder.registerProblem(
					expr,
					"Result of this expression is not used",
					ProblemHighlightType.WARNING,
					new CreateLocalVariableFix(discarded.typeText),
					new DiscardResultFix());
			}
		};
	}

	/** Non-null when {@code expr}'s value is discarded and worth reporting; carries the inferred type. */
	private static @Nullable Discarded classify(@NotNull C3Expr expr)
	{
		// Side-effecting statements that legitimately ignore a value.
		if (expr instanceof C3AssignTypeExpr) return null;
		if (expr instanceof C3BinaryExpr binary && isAssignment(binary)) return null;
		if (expr instanceof C3UnaryExpr unary && isIncrementOrCast(unary)) return null;

		if (expr instanceof C3CallExpr call)
		{
			C3CallExprTail tail = call.getCallExprTail();
			if (tail == null) return null;
			// `mayFail()!` and `mayFail()!!` only unwrap the optional: the value that comes out is
			// still a result, and discarding it is as suspicious as discarding a plain call's.
			boolean unwrapped = isRethrow(tail);
			if (unwrapped)
			{
				if (!(call.getExpr() instanceof C3CallExpr inner)) return null;
				call = inner;
				tail = inner.getCallExprTail();
				if (tail == null) return null;
			}
			// A `(...)` invocation is a real call; ++/--/~ tails are postfix side effects.
			if (tail.getCallInvocation() == null) return null;
			// A trailing block (`@pool() { ... }`) is a body-macro invocation, not a discarded value.
			if (tail.getCompoundStatement() != null) return null;

			// Unknown callees and macros without a written return type stay quiet to avoid noise.
			PsiElement callee = C3Callees.resolve(call);
			C3OptionalType returnType = C3Callees.returnType(callee);
			if (returnType == null || C3Callees.isMayDiscard(callee)) return null;
			String text = returnType.getText().trim();
			if (text.isEmpty() || text.equals("void") || text.equals("void?")) return null;
			if (unwrapped && text.endsWith("?")) text = text.substring(0, text.length() - 1).trim();
			return new Discarded(text);
		}

		// Any other expression statement (`a + b;`, `obj.field;`, a lone name) is pure and useless;
		// its type usually cannot be inferred cheaply, so the fix offers a placeholder type.
		return new Discarded(null);
	}

	private static boolean isAssignment(@NotNull C3BinaryExpr binary)
	{
		for (PsiElement child = binary.getFirstChild(); child != null; child = child.getNextSibling())
		{
			if (child instanceof C3BinaryOp op) return ASSIGNMENT_OPERATORS.contains(op.getText());
		}
		return false;
	}

	private static boolean isRethrow(@NotNull C3CallExprTail tail)
	{
		return tail.getNode().findChildByType(C3Types.BANG) != null
			|| tail.getNode().findChildByType(C3Types.BANGBANG) != null;
	}

	private static boolean isIncrementOrCast(@NotNull C3UnaryExpr unary)
	{
		// A cast operator `(Type)` carries a type; `(void)expr;` is an explicit discard.
		if (unary.getUnaryOp().getType() != null) return true;
		String op = unary.getUnaryOp().getText();
		return op.equals("++") || op.equals("--");
	}

	private record Discarded(@Nullable String typeText)
	{
	}
}
