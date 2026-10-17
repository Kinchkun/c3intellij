package org.c3lang.intellij;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.c3lang.intellij.lexer.C3LexerAdapter;
import org.c3lang.intellij.parser.C3Parser;
import org.c3lang.intellij.psi.C3ElementType;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3Types;
import org.jetbrains.annotations.NotNull;

/**
 * The central wiring for turning C3 text into a parse tree.
 *
 * <p>{@link ParserDefinition} is the single extension point that connects the three lowest layers of
 * any language plugin, which the platform calls in this order whenever a C3 file is opened or edited:
 * <ol>
 *   <li>{@link #createLexer} – splits raw text into tokens (see {@link C3LexerAdapter}).</li>
 *   <li>{@link #createParser} – arranges those tokens into an AST ({@link C3Parser}, generated from
 *       {@code C3.bnf}).</li>
 *   <li>{@link #createElement} / {@link #createFile} – wraps AST nodes in PSI objects, the typed,
 *       navigable tree the rest of the plugin works with ({@link C3Types.Factory}, {@link C3File}).</li>
 * </ol>
 * The remaining methods answer cross-cutting questions: which tokens are whitespace, comments or
 * string literals (delegated to {@link C3TokenSets}), and what the file's root node type is.
 *
 * <p>Registered in {@code plugin.xml} as {@code <lang.parserDefinition language="C3" .../>}.
 */
public class C3ParserDefinition implements ParserDefinition {

    // Comment tokens are produced by the lexer but are not grammar rules, so they get their own
    // element types here. C3TokenSets.COMMENTS groups them for getCommentTokens() below.
    public static IElementType LINE_COMMENT = new C3ElementType("LINE_COMMENT");
    public static IElementType BLOCK_COMMENT = new C3ElementType("BLOCK_COMMENT_LEAD");
    public static IElementType DOC_COMMENT = new C3ElementType("DOC_COMMENT");

    /** Step 1: hand the platform a fresh lexer that turns text into a stream of tokens. */
    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new C3LexerAdapter();
    }

    // The three token-classification methods below let generic platform features (code folding,
    // "comment with line comment", spell-checking of strings, etc.) work without knowing C3 specifics.

    /** Tokens the platform may freely insert/skip between meaningful tokens. */
    @NotNull
    @Override
    public TokenSet getWhitespaceTokens() {
        return C3TokenSets.WHITE_SPACES;
    }

    /** Tokens that are comments (used by the commenter, doc support, folding...). */
    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return C3TokenSets.COMMENTS;
    }

    /** Tokens that hold string literals (used e.g. by spell checking and injection). */
    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return C3TokenSets.STRINGS;
    }

    /** Step 2: the parser that consumes the token stream and builds the AST. Generated from C3.bnf. */
    @NotNull
    @Override
    public PsiParser createParser(final Project project) {
        return new C3Parser();
    }

    /** The element type of the root (file) node. It is stub-aware – see {@link C3FileElementType}. */
    @NotNull
    @Override
    public IFileElementType getFileNodeType() {
        return C3FileElementType.INSTANCE;
    }

    /** Step 3a: wraps the parsed tree of a single file into its PSI file object. */
    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new C3File(viewProvider);
    }

    @Override
    @NotNull
    public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        // MAY = the platform is free to add/remove whitespace between any two tokens (e.g. on reformat).
        return SpaceRequirements.MAY;
    }

    /** Step 3b: wraps an individual AST node into its concrete PSI element. The factory (generated
     *  from C3.bnf) picks the right C3*Impl class based on the node's element type. */
    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        return C3Types.Factory.createElement(node);
    }
}
