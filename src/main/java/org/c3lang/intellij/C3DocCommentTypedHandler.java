package org.c3lang.intellij;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.c3lang.intellij.psi.C3File;
import org.jetbrains.annotations.NotNull;

/**
 * When the user types the doc-comment opener {@code <*}, insert the closing {@code *>}
 * and leave the caret between them.
 */
public final class C3DocCommentTypedHandler extends TypedHandlerDelegate
{
	@Override
	public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file)
	{
		if (c != '*' || !(file instanceof C3File)) return Result.CONTINUE;

		int offset = editor.getCaretModel().getOffset();
		Document document = editor.getDocument();
		CharSequence text = document.getCharsSequence();

		// The just-typed text must be exactly "<*".
		if (offset < 2 || text.charAt(offset - 1) != '*' || text.charAt(offset - 2) != '<') return Result.CONTINUE;
		// Don't add a second closer if one already follows.
		if (offset + 1 < text.length() && text.charAt(offset) == '*' && text.charAt(offset + 1) == '>')
		{
			return Result.CONTINUE;
		}

		document.insertString(offset, "*>");
		editor.getCaretModel().moveToOffset(offset);
		return Result.STOP;
	}
}
