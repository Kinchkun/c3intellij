package org.c3lang.intellij;

import com.intellij.lang.Language;

/**
 * The identity of the C3 language inside the IDE.
 *
 * <p>Every IntelliJ language is represented by a single {@link Language} instance. The platform
 * uses it as the key that ties everything together: a file type declares it speaks {@code C3},
 * and extension points in {@code META-INF/plugin.xml} are registered {@code language="C3"} so the
 * platform knows to route C3 files to <em>our</em> lexer, parser, highlighter, completion, etc.
 *
 * <p>There is exactly one instance ({@link #INSTANCE}); the constructor is private so no other can
 * be created. The {@code "C3"} string passed to {@code super} is the language ID referenced from
 * {@code plugin.xml}.
 *
 * @author Christoffer Lerno
 */
public class C3Language extends Language
{
    /** The single, shared C3 language instance referenced throughout the plugin. */
    public static final C3Language INSTANCE = new C3Language();

    private C3Language()
    {
        // "C3" is the language ID used in plugin.xml (e.g. language="C3").
        super("C3");
    }
}
