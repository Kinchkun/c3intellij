package org.c3lang.intellij;

import com.intellij.lexer.Lexer;
import junit.framework.TestCase;
import org.c3lang.intellij.lexer.C3LexerAdapter;

/**
 * A highlighting lexer must emit tokens that cover every character of the input; otherwise
 * {@code LexerEditorHighlighter} throws "Unexpected termination offset". This guards the open
 * states (strings, chars, comments) against dropping their consumed text when the closing
 * delimiter is missing — which happens when documentation renders an illustrative, incomplete
 * code fragment.
 */
public class C3LexerCoverageTest extends TestCase
{
	private static int lexedEnd(String text)
	{
		Lexer lexer = new C3LexerAdapter();
		lexer.start(text);
		int last = 0;
		while (lexer.getTokenType() != null)
		{
			last = lexer.getTokenEnd();
			lexer.advance();
		}
		return last;
	}

	private static void assertFullyCovered(String text)
	{
		assertEquals("lexer left input uncovered for [" + text + "]", text.length(), lexedEnd(text));
	}

	public void testTerminatedConstructs()
	{
		assertFullyCovered("x = \"abc\";");
		assertFullyCovered("c = 'a';");
		assertFullyCovered("r = `raw`;");
		assertFullyCovered("/* block */ x");
		assertFullyCovered("<* doc *> fn");
	}

	public void testUnterminatedString()
	{
		assertFullyCovered("x = \"abc");
	}

	public void testUnterminatedRawString()
	{
		assertFullyCovered("x = `abc");
	}

	public void testUnterminatedChar()
	{
		assertFullyCovered("x = 'a");
	}

	public void testUnterminatedBlockComment()
	{
		assertFullyCovered("/* unfinished");
	}

	public void testUnterminatedDocComment()
	{
		assertFullyCovered("<* unfinished");
	}

	public void testUnterminatedBytes()
	{
		assertFullyCovered("x\"deadbeef");
		assertFullyCovered("b64`abc");
	}
}
