package org.c3lang.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.ProcessingContext;
import org.c3lang.intellij.C3ParserDefinition;
import org.c3lang.intellij.psi.C3FuncDefinition;
import org.c3lang.intellij.psi.C3MacroDefinition;
import org.c3lang.intellij.psi.C3ParamDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Completion inside C3 doc comments ({@code <* ... *>}): the contract annotations,
 * {@code @param} reference modifiers and the surrounding function/macro parameter names.
 */
public final class DocCommentCompletionContributor extends CompletionProvider<CompletionParameters>
{
	public static final DocCommentCompletionContributor INSTANCE = new DocCommentCompletionContributor();

	private static final List<String> ANNOTATIONS = List.of(
		"@param", "@return", "@return?", "@require", "@ensure", "@deprecated", "@pure");
	private static final List<String> MODIFIERS = List.of(
		"[in]", "[out]", "[inout]", "[&in]", "[&out]", "[&inout]");

	// A line that is (just) an annotation being typed: optional indent, then @word.
	private static final Pattern ANNOTATION_LINE = Pattern.compile("\\s*(@[\\w?]*)");
	// `@param`, an optional reference modifier, then the parameter name being typed.
	private static final Pattern PARAM_NAME_LINE = Pattern.compile("\\s*@param\\s+(\\[[^\\]]*]\\s+)?([\\w$#]*)");

	private DocCommentCompletionContributor() {}

	@Override
	protected void addCompletions(
		@NotNull CompletionParameters parameters,
		@NotNull ProcessingContext context,
		@NotNull CompletionResultSet result)
	{
		PsiElement comment = findDocComment(parameters.getPosition());
		if (comment == null) comment = findDocComment(parameters.getOriginalPosition());
		if (comment == null) return;

		String linePrefix = lineUpToCaret(parameters);

		Matcher paramName = PARAM_NAME_LINE.matcher(linePrefix);
		if (paramName.matches())
		{
			CompletionResultSet matched = result.withPrefixMatcher(paramName.group(2));
			for (String name : parameterNames(comment))
			{
				matched.addElement(LookupElementBuilder.create(name));
			}
			if (paramName.group(1) == null)
			{
				for (String modifier : MODIFIERS) matched.addElement(LookupElementBuilder.create(modifier));
			}
			return;
		}

		Matcher annotation = ANNOTATION_LINE.matcher(linePrefix);
		if (annotation.matches())
		{
			CompletionResultSet matched = result.withPrefixMatcher(annotation.group(1));
			for (String tag : ANNOTATIONS) matched.addElement(LookupElementBuilder.create(tag));
		}
	}

	private static @NotNull String lineUpToCaret(@NotNull CompletionParameters parameters)
	{
		CharSequence text = parameters.getEditor().getDocument().getCharsSequence();
		int offset = Math.min(parameters.getOffset(), text.length());
		int lineStart = offset;
		while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
		return text.subSequence(lineStart, offset).toString();
	}

	private static @Nullable PsiElement findDocComment(@Nullable PsiElement element)
	{
		while (element != null)
		{
			if (element.getNode() != null && element.getNode().getElementType() == C3ParserDefinition.DOC_COMMENT)
			{
				return element;
			}
			element = element.getParent();
		}
		return null;
	}

	private static @NotNull List<String> parameterNames(@NotNull PsiElement comment)
	{
		List<C3ParamDecl> decls = getParamDeclList(comment);
		List<String> names = new java.util.ArrayList<>();
		if (decls == null) return names;
		for (C3ParamDecl decl : decls)
		{
			String name = decl.getParameter().getName();
			if (name != null) names.add(name);
		}
		return names;
	}

	private static @Nullable List<C3ParamDecl> getParamDeclList(@NotNull PsiElement comment)
	{
		PsiElement next = comment.getNextSibling();
		while (next instanceof PsiWhiteSpace || next instanceof PsiComment)
		{
			next = next.getNextSibling();
		}
		if (next == null) return null;

		PsiElement firstChild = next.getFirstChild();
		if (firstChild instanceof C3FuncDefinition funcDef)
		{
			var paramList = funcDef.getFuncDef().getFnParameterList().getParameterList();
			return paramList != null ? paramList.getParamDeclList() : null;
		}
		if (firstChild instanceof C3MacroDefinition macroDef)
		{
			var paramList = macroDef.getMacroParams().getParameterList();
			return paramList != null ? paramList.getParamDeclList() : null;
		}
		return null;
	}
}
