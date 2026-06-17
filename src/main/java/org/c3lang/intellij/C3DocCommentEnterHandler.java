package org.c3lang.intellij;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.c3lang.intellij.psi.C3File;
import org.jetbrains.annotations.NotNull;

/**
 * Continues C3 doc comments: pressing Enter inside a {@code <* ... *>} block prefixes the
 * new line with a single space, matching the one-space indentation convention.
 */
public final class C3DocCommentEnterHandler extends EnterHandlerDelegateAdapter
{
	@Override
	public Result postProcessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext dataContext)
	{
		if (!(file instanceof C3File)) return Result.Continue;

		Document document = editor.getDocument();
		int offset = editor.getCaretModel().getOffset();
		CharSequence text = document.getCharsSequence();

		if (!insideDocComment(text, offset)) return Result.Continue;

		// Add the leading space only when the new line has no indentation yet.
		int lineStart = offset;
		while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
		if (lineStart == offset)
		{
			document.insertString(offset, " ");
			int caret = offset + 1;

			// If the closing `*>` immediately follows (i.e. Enter was pressed in an empty
			// `<*|*>`), move it to its own line so the result is the canonical 3-line form.
			CharSequence updated = document.getCharsSequence();
			if (caret + 1 < updated.length() && updated.charAt(caret) == '*' && updated.charAt(caret + 1) == '>')
			{
				document.insertString(caret, "\n");
			}

			editor.getCaretModel().moveToOffset(caret);
		}
		return Result.Continue;
	}

	/** True if {@code offset} lies inside an open {@code <* ... *>} (nearest delimiter is {@code <*}). */
	private static boolean insideDocComment(@NotNull CharSequence text, int offset)
	{
		for (int i = Math.min(offset, text.length()) - 1; i >= 1; i--)
		{
			char c = text.charAt(i);
			char prev = text.charAt(i - 1);
			if (prev == '*' && c == '>') return false;
			if (prev == '<' && c == '*') return true;
		}
		return false;
	}
}
