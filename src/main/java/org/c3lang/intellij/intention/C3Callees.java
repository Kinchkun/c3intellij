package org.c3lang.intellij.intention;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3AccessIdent;
import org.c3lang.intellij.psi.C3CallExpr;
import org.c3lang.intellij.psi.C3Expr;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3MacroDefinition;
import org.c3lang.intellij.psi.C3OptionalType;
import org.c3lang.intellij.psi.C3PathIdent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Resolves what a call invokes, and reads the declared return type of a function or macro. */
final class C3Callees
{
	private C3Callees()
	{
	}

	/** The function ({@link C3FuncDef}) or macro ({@link C3MacroDefinition}) invoked by {@code call}, or null. */
	static @Nullable PsiElement resolve(@NotNull C3CallExpr call)
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
		return resolved instanceof C3FuncDef || resolved instanceof C3MacroDefinition ? resolved : null;
	}

	/** The written return type of a function or macro; null for a macro without one or any other element. */
	static @Nullable C3OptionalType returnType(@Nullable PsiElement callable)
	{
		if (callable instanceof C3FuncDef funcDef) return funcDef.getFuncHeader().getOptionalType();
		if (callable instanceof C3MacroDefinition macro) return macro.getMacroHeader().getOptionalType();
		return null;
	}
}
