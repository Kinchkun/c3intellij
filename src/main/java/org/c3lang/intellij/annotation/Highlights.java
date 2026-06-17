package org.c3lang.intellij.annotation;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.Font;

public final class Highlights
{
	/**
	 * Doc-comment tag ({@code @param}, {@code @return}, ...). Bold + italic by default.
	 *
	 * <p>The default attributes are supplied directly to {@code createTextAttributesKey} rather
	 * than via {@code getDefaultAttributes()}: the latter resolves a platform service lazily,
	 * which must not happen from a static initializer (the key may be loaded very early, e.g. by
	 * the color-scheme preloader).</p>
	 */
	public static final TextAttributesKey DOC_COMMENT_TAG;

	/**
	 * Markdown ATX heading ({@code # Title}) inside a doc comment. Falls back to the keyword
	 * color so it reads as a distinct accent against the comment text, and is user-configurable
	 * under "Documentation comment//Markdown heading".
	 */
	public static final TextAttributesKey DOC_MARKDOWN_HEADING =
		TextAttributesKey.createTextAttributesKey("C3_DOC_MARKDOWN_HEADING", DefaultLanguageHighlighterColors.KEYWORD);

	static
	{
		TextAttributes tagAttributes = new TextAttributes();
		tagAttributes.setFontType(Font.BOLD | Font.ITALIC);
		DOC_COMMENT_TAG = TextAttributesKey.createTextAttributesKey("C3_DOC_COMMENT_TAG", tagAttributes);
	}

	private Highlights()
	{
	}
}