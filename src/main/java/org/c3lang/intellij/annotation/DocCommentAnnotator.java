package org.c3lang.intellij.annotation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import org.c3lang.intellij.C3ParserDefinition;
import org.c3lang.intellij.C3SyntaxHighlighter;
import org.c3lang.intellij.psi.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DocCommentAnnotator
{
	private DocCommentAnnotator()
	{
	}

	/** Matches an ATX Markdown heading line, e.g. {@code # Title} or {@code ### Sub}. */
	private static final Pattern HEADING_PATTERN = Pattern.compile("^\\s*#{1,6}\\s+\\S.*$");

	/** Matches a fenced code block delimiter, capturing the (optional) info string / language. */
	private static final Pattern FENCE_PATTERN = Pattern.compile("^\\s*(?:```+|~~~+)\\s*([\\w+.#-]*)\\s*$");

	public static void annotateDocComment(PsiComment element, AnnotationHolder holder)
	{
		annotateContractExpressions(element, holder);
		annotateDocTags(element, holder);
		annotateParamTags(element, holder);
		annotateReturnTags(element, holder);
		annotateDeprecatedTags(element, holder);
		annotateStrings(element, holder);
		annotateMarkdown(element, holder);
	}

	/**
	 * Highlights Markdown inside a doc comment: ATX headings ({@code # Title}) and fenced code
	 * blocks. A {@code ```c3} (or unlabelled {@code ```}) block has its content highlighted as C3.
	 *
	 * <p>A doc comment is lexed into a run of adjacent {@code DOC_COMMENT} leaf tokens, so a
	 * multi-line fence spans several elements. We reconstruct the whole comment to track fence
	 * state, then clamp every highlight to the element currently being annotated — the platform
	 * requires annotation ranges to lie within that element.</p>
	 */
	private static void annotateMarkdown(PsiComment element, AnnotationHolder holder)
	{
		PsiElement first = element;
		for (PsiElement prev = first.getPrevSibling(); isDocComment(prev); prev = first.getPrevSibling())
		{
			first = prev;
		}

		StringBuilder full = new StringBuilder();
		for (PsiElement p = first; isDocComment(p); p = p.getNextSibling())
		{
			full.append(p.getText());
		}

		int fullStart = first.getTextRange().getStartOffset();
		int elemStart = element.getTextRange().getStartOffset();
		int elemEnd = element.getTextRange().getEndOffset();
		String text = full.toString();

		C3SyntaxHighlighter highlighter = new C3SyntaxHighlighter();
		boolean inFence = false;
		boolean fenceIsC3 = false;

		int pos = 0;
		while (pos <= text.length())
		{
			int newline = text.indexOf('\n', pos);
			int lineEnd = newline < 0 ? text.length() : newline;
			String line = text.substring(pos, lineEnd);
			int lineStart = fullStart + pos;

			Matcher fence = FENCE_PATTERN.matcher(line);
			if (fence.matches())
			{
				// The ``` / ~~~ delimiter lines are left in the plain comment style (no extra
				// styling) — only the fenced content gets highlighted.
				inFence = !inFence;
				fenceIsC3 = inFence && (fence.group(1).isEmpty() || fence.group(1).equalsIgnoreCase("c3"));
			}
			else if (inFence)
			{
				if (fenceIsC3)
				{
					highlightAsC3(highlighter, line, lineStart, holder, elemStart, elemEnd);
				}
			}
			else if (HEADING_PATTERN.matcher(line).matches())
			{
				markClamped(holder, lineStart, lineStart + line.length(),
					Highlights.DOC_MARKDOWN_HEADING, elemStart, elemEnd);
			}

			if (newline < 0) break;
			pos = newline + 1;
		}
	}

	private static void highlightAsC3(
		C3SyntaxHighlighter highlighter, String code, int codeStart,
		AnnotationHolder holder, int min, int max)
	{
		Lexer lexer = highlighter.getHighlightingLexer();
		lexer.start(code);
		while (lexer.getTokenType() != null)
		{
			TextAttributesKey[] keys = highlighter.getTokenHighlights(lexer.getTokenType());
			if (keys.length > 0)
			{
				markClamped(holder, codeStart + lexer.getTokenStart(), codeStart + lexer.getTokenEnd(),
					keys[0], min, max);
			}
			lexer.advance();
		}
	}

	/** Marks {@code [start, end)} clamped to {@code [min, max)} so it stays within the annotated element. */
	private static void markClamped(AnnotationHolder holder, int start, int end, TextAttributesKey key, int min, int max)
	{
		int s = Math.max(start, min);
		int e = Math.min(end, max);
		if (s < e) mark(holder, s, e, key);
	}

	private static boolean isDocComment(@Nullable PsiElement element)
	{
		return element != null
			&& element.getNode() != null
			&& element.getNode().getElementType() == C3ParserDefinition.DOC_COMMENT;
	}

	/**
	 * Highlights the boolean expressions in {@code @require}/{@code @ensure} contracts as C3
	 * code (operators, identifiers, literals, ...), up to an optional {@code : "description"}.
	 */
	private static void annotateContractExpressions(PsiComment element, AnnotationHolder holder)
	{
		Pattern pattern = Pattern.compile("@(require|ensure)\\s+([^\\n:]*)");
		String commentText = element.getText();
		int commentStart = element.getTextRange().getStartOffset();

		C3SyntaxHighlighter highlighter = new C3SyntaxHighlighter();
		Matcher matcher = pattern.matcher(commentText);
		while (matcher.find())
		{
			String expr = matcher.group(2);
			if (expr.isBlank()) continue;
			int exprStart = commentStart + matcher.start(2);

			Lexer lexer = highlighter.getHighlightingLexer();
			lexer.start(expr);
			while (lexer.getTokenType() != null)
			{
				TextAttributesKey[] keys = highlighter.getTokenHighlights(lexer.getTokenType());
				if (keys.length > 0)
				{
					mark(holder, exprStart + lexer.getTokenStart(), exprStart + lexer.getTokenEnd(), keys[0]);
				}
				lexer.advance();
			}
		}
	}

	public static void annotateStrings(PsiComment element, AnnotationHolder holder)
	{
		Pattern pattern = Pattern.compile("(\"((?:[^\"\\\\]|\\\\.)*)\"|`((?:[^`\\\\]|\\\\.)*)`)");
		String commentText = element.getText();
		int commentStart = element.getTextRange().getStartOffset();

		Matcher matcher = pattern.matcher(commentText);
		while (matcher.find())
		{
			mark(holder, commentStart + matcher.start(), commentStart + matcher.end(), DefaultLanguageHighlighterColors.STRING);
		}
	}

	private static void annotateDeprecatedTags(PsiComment element, AnnotationHolder holder)
	{
		Pattern pattern = Pattern.compile("@deprecated\\s+(\"((?:[^\"\\\\]|\\\\.)*)\"|`((?:[^`\\\\]|\\\\.)*)`)?");
		String commentText = element.getText();
		int commentStart = element.getTextRange().getStartOffset();

		Matcher matcher = pattern.matcher(commentText);
		while (matcher.find())
		{
			String description = matcher.group(1);
			if (description == null) continue;
			mark(holder, commentStart + matcher.start(1), commentStart + matcher.end(1), DefaultLanguageHighlighterColors.STRING);
		}
	}

	private static void annotateReturnTags(PsiComment element, AnnotationHolder holder)
	{
		Pattern pattern = Pattern.compile("@return\\s+(\"((?:[^\"\\\\]|\\\\.)*)\"|`((?:[^`\\\\]|\\\\.)*)`)?");
		String commentText = element.getText();
		int commentStart = element.getTextRange().getStartOffset();

		Matcher matcher = pattern.matcher(commentText);
		while (matcher.find())
		{
			String description = matcher.group(1);
			if (description == null) continue;
			mark(holder, commentStart + matcher.start(1), commentStart + matcher.end(1), DefaultLanguageHighlighterColors.STRING);
		}
	}

	private static void annotateDocTags(PsiComment element, AnnotationHolder holder)
	{
		Pattern pattern = Pattern.compile("@(param|return(\\?)?|deprecated|require|ensure|pure)");
		String commentText = element.getText();
		int commentStart = element.getTextRange().getStartOffset();

		Matcher matcher = pattern.matcher(commentText);
		while (matcher.find())
		{
			mark(holder, commentStart + matcher.start(), commentStart + matcher.end(), DefaultLanguageHighlighterColors.DOC_COMMENT_TAG);
		}
	}

	private static void addParameters(List<String> args, @Nullable C3ParameterList parameterList)
	{
		if (parameterList == null) return;
		List<C3ParamDecl> decls = parameterList.getParamDeclList();
		for (C3ParamDecl decl : decls)
		{
			C3Parameter parameter = decl.getParameter();
			if (parameter.getName() != null)
			{
				args.add(parameter.getName());
			}
			else if (parameter.getType() != null)
			{
				args.add(parameter.getType().getText());
			}
		}
	}

	private static void annotateParamTags(PsiComment element, AnnotationHolder holder)
	{
		PsiElement next = element.getNextSibling();

		while (next instanceof PsiWhiteSpace || next instanceof PsiComment)
		{
			next = next.getNextSibling();
		}

		if (next instanceof C3DefaultModuleSection) next = next.getFirstChild();

		ArrayList<String> args = new ArrayList<>();
		boolean is_function = false;
		if (next != null)
		{
			next = next.getFirstChild();
			if (next instanceof C3FuncDefinition d)
			{
				is_function = true;
				addParameters(args, d.getFuncDef().getFnParameterList().getParameterList());
			}
			else if (next instanceof C3MacroDefinition d)
			{
				addParameters(args, d.getMacroParams().getParameterList());
			}
		}

		Pattern pattern = Pattern.compile(
				"@param\\s+((\\[(in|&in|out|&out|inout|&inout)])\\s+)?(([$#])?\\w+)(\\s+:\\s+(\"((?:[^\"\\\\]|\\\\.)*)\"|`((?:[^`\\\\]|\\\\.)*)`))?");
		String commentText = element.getText();
		int commentStart = element.getTextRange().getStartOffset();

		Matcher matcher = pattern.matcher(commentText);
		while (matcher.find())
		{
			String contract = matcher.group(1);
			String name = matcher.group(4);
			String description = matcher.group(6);

			if (contract != null)
			{
				mark(holder, commentStart + matcher.start(2), commentStart + matcher.end(2), DefaultLanguageHighlighterColors.CONSTANT);
			}

			if (description != null)
			{
				mark(holder, commentStart + matcher.start(6), commentStart + matcher.end(6), DefaultLanguageHighlighterColors.STRING);
			}

			if (name != null)
			{
				if (!args.contains(name))
				{
					TextRange range = TextRange.create(commentStart + matcher.start(), commentStart + matcher.end());
					holder.newAnnotation(HighlightSeverity.ERROR, "There is no argument named '" + name + "' in this " + (is_function ? "function." : "macro.")).range(range).create();
				}

				mark(holder, commentStart + matcher.start(4), commentStart + matcher.end(4), DefaultLanguageHighlighterColors.NUMBER);
			}
		}
	}
	private static void mark(AnnotationHolder holder, int start, int end, TextAttributesKey highlight)
	{
		TextRange nameRange = TextRange.create(start, end);

		holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
		      .range(nameRange)
		      .textAttributes(highlight)
		      .create();

	}
}