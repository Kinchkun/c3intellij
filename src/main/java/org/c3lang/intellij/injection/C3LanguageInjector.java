package org.c3lang.intellij.injection;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.docs.DocumentationUtils;
import org.c3lang.intellij.psi.C3AccessIdent;
import org.c3lang.intellij.psi.C3Arg;
import org.c3lang.intellij.psi.C3ArgList;
import org.c3lang.intellij.psi.C3CallExpr;
import org.c3lang.intellij.psi.C3Expr;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3MacroDefinition;
import org.c3lang.intellij.psi.C3ParamDecl;
import org.c3lang.intellij.psi.C3PathIdentExpr;
import org.c3lang.intellij.psi.C3StringExpr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Injects a language (JSON, XML, SQL, ...) into a C3 string-literal argument when the called
 * function/macro documents the parameter with a {@code @language <param>: <lang>} contract, e.g.
 *
 * <pre>{@code
 * <* @language query: sql *>
 * fn void run(String query) { ... }
 *
 * run("SELECT * FROM users");   // <- the string is highlighted (and analysed) as SQL
 * }</pre>
 *
 * Once SQL is injected, IntelliJ's database tooling resolves it against a configured data source
 * automatically — that part is a platform feature, not something this injector does.
 */
public final class C3LanguageInjector implements MultiHostInjector
{
	private static final Pattern LANGUAGE_PATTERN =
		Pattern.compile("@language\\s+(\\w+)\\s*:\\s*([\\w+.#-]+)");

	@Override
	public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn()
	{
		return List.of(C3StringExpr.class);
	}

	@Override
	public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context)
	{
		if (!(context instanceof C3StringExpr str)) return;
		if (!(context instanceof PsiLanguageInjectionHost host) || !host.isValidHost()) return;

		String languageId = languageForArgument(str);
		if (languageId == null) return;

		Language language = findLanguage(languageId);
		if (language == null) return;

		TextRange contentRange = contentRange(str);
		if (contentRange == null) return;

		registrar.startInjecting(language)
			.addPlace(null, null, host, contentRange)
			.doneInjecting();
	}

	private static @Nullable String languageForArgument(@NotNull C3StringExpr str)
	{
		C3Arg arg = PsiTreeUtil.getParentOfType(str, C3Arg.class);
		if (arg == null) return null;

		C3ArgList argList = PsiTreeUtil.getParentOfType(arg, C3ArgList.class);
		C3CallExpr call = PsiTreeUtil.getParentOfType(arg, C3CallExpr.class);
		if (argList == null || call == null) return null;

		PsiElement callee = resolveCallee(call);
		if (callee == null) return null;

		String paramName = parameterName(arg, argList, callee);
		if (paramName == null) return null;

		String doc = documentationOf(callee);
		if (doc.isEmpty()) return null;

		Matcher matcher = LANGUAGE_PATTERN.matcher(doc);
		while (matcher.find())
		{
			if (matcher.group(1).equals(paramName)) return matcher.group(2);
		}
		return null;
	}

	/** Resolves the function/macro a call invokes (free function or {@code obj.method}). */
	private static @Nullable PsiElement resolveCallee(@NotNull C3CallExpr call)
	{
		C3Expr callee = call.getExpr();
		if (callee instanceof C3PathIdentExpr pathIdentExpr)
		{
			return resolve(pathIdentExpr.getPathIdent().getReference());
		}
		// `obj.method(args)` nests as call(expr = call(obj, .method), invocation): the method is the
		// access-ident tail of the inner call.
		if (callee instanceof C3CallExpr inner && inner.getCallExprTail() != null)
		{
			C3AccessIdent access = inner.getCallExprTail().getAccessIdent();
			if (access != null) return resolve(access.getReference());
		}
		return null;
	}

	private static @Nullable PsiElement resolve(@Nullable PsiReference reference)
	{
		return reference != null ? reference.resolve() : null;
	}

	private static @Nullable String parameterName(
		@NotNull C3Arg arg, @NotNull C3ArgList argList, @NotNull PsiElement callee)
	{
		// Named argument: `foo(myParam: "...")`.
		if (arg.getNamedIdent() != null)
		{
			return arg.getNamedIdent().getText();
		}
		// Positional argument: map by index into the callee's parameter list.
		int index = argList.getArgList().indexOf(arg);
		if (index < 0) return null;
		List<C3ParamDecl> params = parametersOf(callee);
		if (index >= params.size()) return null;
		return params.get(index).getParameter().getName();
	}

	private static @NotNull List<C3ParamDecl> parametersOf(@NotNull PsiElement callee)
	{
		if (callee instanceof C3FuncDef funcDef
			&& funcDef.getFnParameterList() != null
			&& funcDef.getFnParameterList().getParameterList() != null)
		{
			return funcDef.getFnParameterList().getParameterList().getParamDeclList();
		}
		if (callee instanceof C3MacroDefinition macro
			&& macro.getMacroParams() != null
			&& macro.getMacroParams().getParameterList() != null)
		{
			return macro.getMacroParams().getParameterList().getParamDeclList();
		}
		return List.of();
	}

	private static @NotNull String documentationOf(@NotNull PsiElement callee)
	{
		if (callee instanceof C3FuncDef funcDef && funcDef.getParent() != null)
		{
			return DocumentationUtils.findDocumentationComment(funcDef.getParent());
		}
		if (callee instanceof C3MacroDefinition)
		{
			return DocumentationUtils.findDocumentationComment(callee);
		}
		return "";
	}

	private static @Nullable Language findLanguage(@NotNull String id)
	{
		return switch (id.toLowerCase(Locale.ROOT))
		{
			case "json" -> Language.findLanguageByID("JSON");
			case "xml" -> Language.findLanguageByID("XML");
			case "sql" -> Language.findLanguageByID("SQL");
			case "html" -> Language.findLanguageByID("HTML");
			case "yaml", "yml" -> Language.findLanguageByID("yaml");
			case "regexp", "regex" -> Language.findLanguageByID("RegExp");
			default -> Language.findLanguageByID(id.toUpperCase(Locale.ROOT));
		};
	}

	/** The range inside the string literal that excludes its delimiters. */
	private static @Nullable TextRange contentRange(@NotNull C3StringExpr str)
	{
		String text = str.getText();
		if (text.length() < 2) return null;

		char first = text.charAt(0);
		if (first != '"' && first != '`') return null;

		int start = 1;
		int end = text.charAt(text.length() - 1) == first ? text.length() - 1 : text.length();
		if (end <= start) return null;
		return TextRange.create(start, end);
	}
}
