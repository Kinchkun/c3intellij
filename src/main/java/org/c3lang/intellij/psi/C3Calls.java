package org.c3lang.intellij.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Shared helpers for working with call sites ({@code foo(...)}, {@code recv.method(...)}). */
public final class C3Calls
{
	private C3Calls() {}

	/**
	 * The function/method definitions a call invocation could be targeting, overloads included.
	 * Empty when the callee cannot be resolved.
	 */
	public static @NotNull List<C3CallablePsiElement> resolveCallables(@NotNull C3CallInvocation invocation)
	{
		C3CallExpr call = PsiTreeUtil.getParentOfType(invocation, C3CallExpr.class);
		C3Expr callee = call != null ? call.getExpr() : null;
		if (callee == null) return List.of();

		PsiElement nameElement;
		if (callee instanceof C3CallExpr inner
			&& inner.getCallExprTail() != null
			&& inner.getCallExprTail().getAccessIdent() != null)
		{
			nameElement = inner.getCallExprTail().getAccessIdent(); // method call: `recv.method(...)`
		}
		else
		{
			nameElement = PsiTreeUtil.findChildOfType(callee, C3PathIdent.class); // `foo(...)` / `mod::foo(...)`
		}
		if (nameElement == null) return List.of();

		PsiReference reference = nameElement instanceof C3AccessIdent accessIdent
			? accessIdent.getReference()
			: ((C3PathIdent) nameElement).getReference();
		if (reference == null) return List.of();

		List<C3CallablePsiElement> callables = new ArrayList<>();
		if (reference instanceof PsiPolyVariantReference poly)
		{
			for (ResolveResult result : poly.multiResolve(false))
			{
				if (result.getElement() instanceof C3CallablePsiElement callable) callables.add(callable);
			}
		}
		else if (reference.resolve() instanceof C3CallablePsiElement callable)
		{
			callables.add(callable);
		}
		return callables;
	}
}
