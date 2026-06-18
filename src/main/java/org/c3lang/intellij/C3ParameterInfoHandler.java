package org.c3lang.intellij;

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.lang.parameterInfo.ParameterInfoUtils;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3ArgList;
import org.c3lang.intellij.psi.C3CallArgList;
import org.c3lang.intellij.psi.C3CallInvocation;
import org.c3lang.intellij.psi.C3CallablePsiElement;
import org.c3lang.intellij.psi.C3Calls;
import org.c3lang.intellij.psi.C3Types;
import org.c3lang.intellij.psi.ParamType;
import org.c3lang.intellij.psi.ShortType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shows the parameter list of the function/method being called while the caret is inside its
 * argument list ({@code Ctrl+P}, or automatically after completing a call). The current argument is
 * highlighted as commas are crossed.
 */
public final class C3ParameterInfoHandler implements ParameterInfoHandler<C3CallInvocation, C3CallablePsiElement>
{
	@Override
	public @Nullable C3CallInvocation findElementForParameterInfo(@NotNull CreateParameterInfoContext context)
	{
		C3CallInvocation invocation = invocationAt(context.getFile().findElementAt(context.getOffset()));
		if (invocation == null) return null;

		List<C3CallablePsiElement> callables = C3Calls.resolveCallables(invocation);
		if (callables.isEmpty()) return null;

		context.setItemsToShow(callables.toArray());
		return invocation;
	}

	@Override
	public void showParameterInfo(@NotNull C3CallInvocation invocation, @NotNull CreateParameterInfoContext context)
	{
		context.showHint(invocation, invocation.getTextRange().getStartOffset(), this);
	}

	@Override
	public @Nullable C3CallInvocation findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context)
	{
		return invocationAt(context.getFile().findElementAt(context.getOffset()));
	}

	@Override
	public void updateParameterInfo(@NotNull C3CallInvocation invocation, @NotNull UpdateParameterInfoContext context)
	{
		C3CallArgList callArgList = invocation.getCallArgList();
		C3ArgList argList = callArgList != null ? callArgList.getArgList() : null;
		int index = argList != null
			? ParameterInfoUtils.getCurrentParameterIndex(argList.getNode(), context.getOffset(), C3Types.COMMA)
			: 0;
		context.setCurrentParameter(index);
	}

	@Override
	public void updateUI(C3CallablePsiElement callable, @NotNull ParameterInfoUIContext context)
	{
		List<ParamType> parameters = callable.getParameterTypes();
		if (parameters.isEmpty())
		{
			context.setupUIComponentPresentation(
				"<no parameters>", -1, -1, false, false, false, context.getDefaultParameterColor());
			return;
		}

		StringBuilder signature = new StringBuilder();
		int highlightStart = -1;
		int highlightEnd = -1;
		int current = context.getCurrentParameterIndex();
		for (int i = 0; i < parameters.size(); i++)
		{
			if (i > 0) signature.append(", ");
			int start = signature.length();
			signature.append(present(parameters.get(i)));
			if (i == current)
			{
				highlightStart = start;
				highlightEnd = signature.length();
			}
		}

		context.setupUIComponentPresentation(
			signature.toString(), highlightStart, highlightEnd, false, false, false,
			context.getDefaultParameterColor());
	}

	private static @NotNull String present(@NotNull ParamType parameter)
	{
		ShortType type = parameter.getType();
		String name = parameter.getName();
		if (type == null) return name != null ? name : "?";
		return name != null && !name.isEmpty() ? type.getFullName() + " " + name : type.getFullName();
	}

	/** The innermost call invocation ({@code ( ... )}) enclosing {@code element}, or null. */
	private static @Nullable C3CallInvocation invocationAt(@Nullable PsiElement element)
	{
		return PsiTreeUtil.getParentOfType(element, C3CallInvocation.class);
	}
}
