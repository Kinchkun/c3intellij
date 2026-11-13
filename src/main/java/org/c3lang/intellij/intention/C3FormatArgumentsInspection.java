package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.c3lang.intellij.psi.C3Arg;
import org.c3lang.intellij.psi.C3ArgList;
import org.c3lang.intellij.psi.C3Attribute;
import org.c3lang.intellij.psi.C3Attributes;
import org.c3lang.intellij.psi.C3CallExpr;
import org.c3lang.intellij.psi.C3CallExprTail;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3MacroDefinition;
import org.c3lang.intellij.psi.C3ParamDecl;
import org.c3lang.intellij.psi.C3Parameter;
import org.c3lang.intellij.psi.C3ParameterList;
import org.c3lang.intellij.psi.C3StringExpr;
import org.c3lang.intellij.psi.C3Types;
import org.c3lang.intellij.psi.C3Visitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Compares the placeholders of a format string with the arguments that follow it:
 * {@code io::printfn("status=%s body=", code, body)} passes one argument too many.
 *
 * <p>A function or macro marked {@code @format(n)} takes its format string as parameter {@code n}
 * ({@code io::printfn}, {@code string::tformat}, {@code DString.appendf}, {@code log::info});
 * there the compiler reports a mismatch ("Too many arguments were provided for the formatting
 * string"), and so does this inspection, as an error. The standard library has format functions
 * without the attribute ({@code io::eprintfn}), which the compiler does not check: a parameter
 * {@code String format} or {@code String fmt} directly followed by {@code args...} is taken for a
 * format string too, and a mismatch there is a warning. At run time a missing argument prints
 * {@code <MISSING>} and an extra one is dropped.</p>
 *
 * <p>Only a format written as a string literal is checked, and only when the arguments are all
 * written out (no {@code ...splat}).</p>
 */
public final class C3FormatArgumentsInspection extends LocalInspectionTool
{
	private static final Set<String> FORMAT_PARAMETER_NAMES = Set.of("format", "fmt");

	@Override
	public @NotNull String getDisplayName()
	{
		return "Format string arguments";
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
			public void visitCallExpr(@NotNull C3CallExpr call)
			{
				List<C3Arg> arguments = arguments(call);
				if (arguments == null || !hasStringLiteral(arguments)) return;

				PsiElement callee = C3Callees.resolve(call);
				FormatParameter format = formatParameter(callee);
				if (format == null) return;

				// `text.appendf(...)`: the receiver is parameter 0 and no argument.
				int formatIndex = format.index - (isMethodCall(call) && hasReceiver(callee) ? 1 : 0);
				int provided = 0;
				C3StringExpr formatString = null;
				int position = 0;
				for (C3Arg argument : arguments)
				{
					if (argument.getNode().findChildByType(C3Types.ELLIPSIS) != null) return; // a splat: unknown count
					if (argument.getNamedIdent() != null || argument.getParamPath() != null) continue; // `category: x`
					if (position == formatIndex)
					{
						if (!(argument.getExpr() instanceof C3StringExpr literal)) return;
						formatString = literal;
					}
					else if (position > formatIndex)
					{
						provided++;
					}
					position++;
				}
				if (formatString == null) return;

				String literal = formatString.getText();
				// Without the quotes, so a format ending in `%` is seen as incomplete.
				if (literal.length() >= 2) literal = literal.substring(1, literal.length() - 1);
				int expected = placeholders(literal);
				if (expected < 0 || expected == provided) return;

				holder.registerProblem(
					formatString,
					(provided < expected ? "Too few" : "Too many") + " arguments were provided for the formatting string: "
						+ count(expected, "placeholder") + ", " + count(provided, "argument"),
					format.checkedByCompiler ? ProblemHighlightType.GENERIC_ERROR : ProblemHighlightType.WARNING);
			}
		};
	}

	private static @NotNull String count(int number, @NotNull String noun)
	{
		return number + " " + noun + (number == 1 ? "" : "s");
	}

	/** Where a callable takes its format string, and whether the compiler checks the arguments against it. */
	private record FormatParameter(int index, boolean checkedByCompiler) {}

	private static @Nullable FormatParameter formatParameter(@Nullable PsiElement callee)
	{
		C3Attributes attributes;
		C3ParameterList parameters;
		if (callee instanceof C3FuncDef function)
		{
			attributes = function.getAttributes();
			parameters = function.getFnParameterList().getParameterList();
		}
		else if (callee instanceof C3MacroDefinition macro)
		{
			attributes = macro.getAttributes();
			parameters = macro.getMacroParams().getParameterList();
		}
		else
		{
			return null;
		}

		Integer declared = formatAttributeIndex(attributes);
		if (declared != null) return new FormatParameter(declared, true);

		// No @format: a `String format` (or `fmt`) directly followed by `args...`, as in io::eprintfn.
		if (parameters == null) return null;
		List<C3ParamDecl> declarations = parameters.getParamDeclList();
		for (int index = 0; index + 1 < declarations.size(); index++)
		{
			C3Parameter parameter = declarations.get(index).getParameter();
			if (parameter.getType() == null || !parameter.getType().getText().equals("String")) continue;
			String name = parameter.getName();
			if (name == null || !FORMAT_PARAMETER_NAMES.contains(name)) continue;
			if (isUntypedVarargs(declarations.get(index + 1).getParameter())) return new FormatParameter(index, false);
		}
		return null;
	}

	/** The {@code n} of {@code @format(n)}, or null without the attribute. */
	private static @Nullable Integer formatAttributeIndex(@Nullable C3Attributes attributes)
	{
		if (attributes == null) return null;
		for (C3Attribute attribute : attributes.getAttributeList())
		{
			if (!attribute.getAttributeName().getText().equals("@format") || attribute.getAttributeParamList() == null) continue;
			try
			{
				return Integer.parseInt(attribute.getAttributeParamList().getText().trim());
			}
			catch (NumberFormatException ignored)
			{
				return null;
			}
		}
		return null;
	}

	/** {@code args...}: any number of values of any type. */
	private static boolean isUntypedVarargs(@NotNull C3Parameter parameter)
	{
		return parameter.getType() == null && parameter.getNode().findChildByType(C3Types.ELLIPSIS) != null;
	}

	private static boolean hasReceiver(@Nullable PsiElement callee)
	{
		if (callee instanceof C3FuncDef function) return function.getType() != null;
		return callee instanceof C3MacroDefinition macro && macro.getType() != null;
	}

	/** {@code receiver.method(...)}: the callee is a member access. */
	private static boolean isMethodCall(@NotNull C3CallExpr call)
	{
		return call.getExpr() instanceof C3CallExpr receiver
			&& receiver.getCallExprTail() != null
			&& receiver.getCallExprTail().getAccessIdent() != null;
	}

	/** The arguments of a `(...)` invocation; null for any other call tail and for a body-macro call. */
	private static @Nullable List<C3Arg> arguments(@NotNull C3CallExpr call)
	{
		C3CallExprTail tail = call.getCallExprTail();
		if (tail == null || tail.getCallInvocation() == null) return null;
		C3ArgList list = tail.getCallInvocation().getCallArgList().getArgList();
		return list != null ? list.getArgList() : new ArrayList<>();
	}

	private static boolean hasStringLiteral(@NotNull List<C3Arg> arguments)
	{
		for (C3Arg argument : arguments)
		{
			if (argument.getExpr() instanceof C3StringExpr) return true;
		}
		return false;
	}

	/**
	 * The number of arguments a format string consumes, as {@code Formatter.vprintf} reads it:
	 * {@code %[flags][width][.precision]specifier}, where {@code %%} is a literal percent sign and a
	 * {@code *} width or precision takes an argument of its own. -1 for a format that ends inside a
	 * placeholder.
	 */
	static int placeholders(@NotNull String format)
	{
		int count = 0;
		int length = format.length();
		for (int index = 0; index < length; index++)
		{
			if (format.charAt(index) != '%') continue;
			if (++index >= length) return -1;
			if (format.charAt(index) == '%') continue;

			while ("0-+ #".indexOf(format.charAt(index)) >= 0)
			{
				if (++index >= length) return -1;
			}
			index = skipField(format, index);
			if (index < 0) return -1;
			if (format.charAt(index) == '*')
			{
				count++;
				if (++index >= length) return -1;
			}
			if (format.charAt(index) == '.')
			{
				if (++index >= length) return -1;
				index = skipField(format, index);
				if (index < 0) return -1;
				if (format.charAt(index) == '*')
				{
					count++;
					if (++index >= length) return -1;
				}
			}
			count++; // the specifier character at `index`
		}
		return count;
	}

	/** The index after the digits starting at {@code index}; -1 when the format ends there. */
	private static int skipField(@NotNull String format, int index)
	{
		while (index < format.length() && Character.isDigit(format.charAt(index))) index++;
		return index < format.length() ? index : -1;
	}
}
