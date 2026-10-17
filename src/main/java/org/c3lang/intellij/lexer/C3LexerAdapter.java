package org.c3lang.intellij.lexer;

import com.intellij.lexer.FlexAdapter;

/**
 * Adapts the generated JFlex lexer to the platform's {@link com.intellij.lexer.Lexer} API.
 *
 * <p>The actual tokenizer, {@code C3Lexer}, is <strong>generated</strong> from {@code C3.flex} by the
 * Gradle {@code generateLexer} task into {@code src/main/gen} – never edit it by hand. JFlex lexers
 * implement a low-level interface; {@link FlexAdapter} bridges that to the richer {@code Lexer}
 * interface the platform expects. This tiny wrapper simply plugs our generated lexer into the adapter.
 *
 * <p>Handed to the platform by {@link org.c3lang.intellij.C3ParserDefinition#createLexer}.
 */
public class C3LexerAdapter extends FlexAdapter
{
    public C3LexerAdapter()
    {
        // C3Lexer(null) – the argument is the input reader, which FlexAdapter resets for each use.
        super(new C3Lexer(null));
    }
}
