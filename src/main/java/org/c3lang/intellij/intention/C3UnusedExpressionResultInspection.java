package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3AccessIdent;
import org.c3lang.intellij.psi.C3AssignTypeExpr;
import org.c3lang.intellij.psi.C3Attribute;
import org.c3lang.intellij.psi.C3Attributes;
import org.c3lang.intellij.psi.C3BinaryExpr;
import org.c3lang.intellij.psi.C3BinaryOp;
import org.c3lang.intellij.psi.C3CallExpr;
import org.c3lang.intellij.psi.C3CallExprTail;
import org.c3lang.intellij.psi.C3Expr;
import org.c3lang.intellij.psi.C3ExprStmt;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3PathIdent;
import org.c3lang.intellij.psi.C3UnaryExpr;
import org.c3lang.intellij.psi.C3Visitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Warns when a statement evaluates an expression but discards its result — e.g. {@code compute();}
 * where {@code compute} returns a value that is never stored, returned or otherwise used. The
 * <i>Create local variable</i> quick fix captures the value in a new declaration.
 *
 * <p>Statements that exist for their side effect are not reported: assignments, increments
 * ({@code x++}), optional-unwraps ({@code mayFail()!}) and explicit {@code (void)} discards. Calls
 * are only reported when the callee resolves to a function with a non-{@code void} return type that
 * is not {@code @maydiscard} — so calling a {@code void} function as a statement stays clean.</p>
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
					new CreateLocalVariableFix(discarded.typeText));
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
			// A `(...)` invocation is a real call; ++/--/!/!!/~ tails are postfix side effects.
			if (tail == null || tail.getCallInvocation() == null) return null;
			// A trailing block (`@pool() { ... }`) is a body-macro invocation, not a discarded value.
			if (tail.getCompoundStatement() != null) return null;

			C3FuncDef callee = resolveCallee(call);
			if (callee == null) return null; // unknown callee (e.g. a macro) — stay quiet to avoid noise

			String returnType = returnTypeText(callee);
			if (returnType == null || returnType.equals("void") || isMayDiscard(callee)) return null;
			return new Discarded(returnType);
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

	private static boolean isIncrementOrCast(@NotNull C3UnaryExpr unary)
	{
		// A cast operator `(Type)` carries a type; `(void)expr;` is an explicit discard.
		if (unary.getUnaryOp().getType() != null) return true;
		String op = unary.getUnaryOp().getText();
		return op.equals("++") || op.equals("--");
	}

	/** Resolves the function/method being invoked by {@code call} to its definition, or null. */
	private static @Nullable C3FuncDef resolveCallee(@NotNull C3CallExpr call)
	{
		C3Expr callee = call.getExpr();
		if (callee == null) return null;

		PsiElement nameElement;
		if (callee instanceof C3CallExpr inner
			&& inner.getCallExprTail() != null
			&& inner.getCallExprTail().getAccessIdent() != null)
		{
			nameElement = inner.getCallExprTail().getAccessIdent(); // method call: `recv.method()`
		}
		else
		{
			nameElement = PsiTreeUtil.findChildOfType(callee, C3PathIdent.class); // `foo()` / `mod::foo()`
		}
		if (nameElement == null) return null;

		PsiReference reference = nameElement instanceof C3AccessIdent accessIdent
			? accessIdent.getReference()
			: ((C3PathIdent) nameElement).getReference();
		PsiElement resolved = reference != null ? reference.resolve() : null;
		return resolved instanceof C3FuncDef funcDef ? funcDef : null;
	}

	private static @Nullable String returnTypeText(@NotNull C3FuncDef funcDef)
	{
		PsiElement optionalType = funcDef.getFuncHeader().getOptionalType();
		if (optionalType == null) return null;
		String text = optionalType.getText().trim();
		return text.isEmpty() ? null : text;
	}

	private static boolean isMayDiscard(@NotNull C3FuncDef funcDef)
	{
		C3Attributes attributes = funcDef.getAttributes();
		if (attributes == null) return false;
		for (C3Attribute attribute : attributes.getAttributeList())
		{
			String text = attribute.getAttributeName().getText();
			String simple = text.substring(text.lastIndexOf(':') + 1).replace("@", "").toLowerCase(Locale.ROOT);
			if (simple.equals("maydiscard")) return true;
		}
		return false;
	}

	private record Discarded(@Nullable String typeText)
	{
	}
}
