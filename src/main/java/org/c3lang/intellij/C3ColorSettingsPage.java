package org.c3lang.intellij;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Undocumented Class
 *
 * @author Christoffer Lerno
 */
public class C3ColorSettingsPage implements ColorSettingsPage
{
    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[] {
        new AttributesDescriptor("Syntax error", C3SyntaxHighlighter.BAD_CHARACTER),
        new AttributesDescriptor("Identifiers", C3SyntaxHighlighter.IDENTIFIER_KEY),
        new AttributesDescriptor("Numbers", C3SyntaxHighlighter.NUMBER_KEY),
        new AttributesDescriptor("Strings", C3SyntaxHighlighter.STRING_KEY),
        new AttributesDescriptor("Types", C3SyntaxHighlighter.TYPE_KEY),
        new AttributesDescriptor("Constants", C3SyntaxHighlighter.CONSTANT_KEY),
        new AttributesDescriptor("Faults", C3SyntaxHighlighter.CONST_IDENT_FAULT_KEY),
        new AttributesDescriptor("Attributes", C3SyntaxHighlighter.ATTRIBUTE_KEY),
        new AttributesDescriptor("Function & macro declarations//Function", C3SyntaxHighlighter.FUNCTION_KEY),
        new AttributesDescriptor("Function & macro declarations//Macro", C3SyntaxHighlighter.MACRO_KEY),
        new AttributesDescriptor("Function & macro declarations//@Macro", C3SyntaxHighlighter.AT_MACRO_KEY),
        new AttributesDescriptor("Function & macro declarations//Method", C3SyntaxHighlighter.METHOD_KEY),
        new AttributesDescriptor("Function & macro declarations//Macro method", C3SyntaxHighlighter.MACRO_METHOD_KEY),
        new AttributesDescriptor("Function & macro declarations//@Macro method", C3SyntaxHighlighter.AT_MACRO_METHOD_KEY),
        new AttributesDescriptor("Keywords", C3SyntaxHighlighter.KEYWORD_KEY),
        new AttributesDescriptor("Keywords (compile time)", C3SyntaxHighlighter.CT_KEYWORD_KEY),
        new AttributesDescriptor("Braces and Operators//Braces", C3SyntaxHighlighter.BRACES_KEY),
        new AttributesDescriptor("Braces and Operators//Brackets", C3SyntaxHighlighter.BRACKETS_KEY),
        new AttributesDescriptor("Braces and Operators//Comma", C3SyntaxHighlighter.COMMA_KEY),
        new AttributesDescriptor("Braces and Operators//Dot", C3SyntaxHighlighter.DOT_KEY),
        new AttributesDescriptor("Braces and Operators//Parentheses", C3SyntaxHighlighter.PARENTHESES_KEY),
        new AttributesDescriptor("Braces and Operators//Operator", C3SyntaxHighlighter.OPERATOR_KEY),
        new AttributesDescriptor("Braces and Operators//Semicolon", C3SyntaxHighlighter.EOS_KEY),
        new AttributesDescriptor("Type definition", C3SyntaxHighlighter.TYPE_DEFINITION_KEY),
        new AttributesDescriptor("Type definition//Enum", C3SyntaxHighlighter.ENUM_NAME_KEY),
        new AttributesDescriptor("Type definition//Struct", C3SyntaxHighlighter.STRUCT_NAME_KEY),
        new AttributesDescriptor("Type definition//Union", C3SyntaxHighlighter.UNION_NAME_KEY),
        new AttributesDescriptor("Type definition//Fault", C3SyntaxHighlighter.FAULT_NAME_KEY),
        new AttributesDescriptor("Type definition//Bitstruct", C3SyntaxHighlighter.BITSTRUCT_NAME_KEY),
        new AttributesDescriptor("Type definition//Typedef", C3SyntaxHighlighter.TYPEDEF_NAME_KEY),
        new AttributesDescriptor("Type definition//Type alias", C3SyntaxHighlighter.ALIAS_TYPE_NAME_KEY),
        new AttributesDescriptor("Type definition//Attribute definition", C3SyntaxHighlighter.ATTRDEF_ATTRIBUTE_KEY),
        new AttributesDescriptor("Alias name", C3SyntaxHighlighter.ALIAS_NAME_KEY),
        new AttributesDescriptor("Bytes", C3SyntaxHighlighter.BYTES_KEY),
        new AttributesDescriptor("Comments//Line comment", C3SyntaxHighlighter.LINE_COMMENT_KEY),
        new AttributesDescriptor("Comments//Block comment", C3SyntaxHighlighter.BLOCK_COMMENT_KEY),
        new AttributesDescriptor("Comments//Documentation comment", C3SyntaxHighlighter.DOC_COMMENT_KEY),
        new AttributesDescriptor("Comments//Documentation comment//Markdown heading", org.c3lang.intellij.annotation.Highlights.DOC_MARKDOWN_HEADING),
        new AttributesDescriptor("Strings//Escape sequence", C3SyntaxHighlighter.ESCAPE_SEQ_KEY),
        new AttributesDescriptor("Strings//Invalid escape sequence", C3SyntaxHighlighter.INVALID_ESCAPE_SEQ_KEY),
    };


    @Override
    public @Nullable Icon getIcon() {
        return C3Icons.FILE;
    }


    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new C3SyntaxHighlighter();
    }

    @Override
    public @NonNls @NotNull String getDemoText() {
        return C3ColorSettingsPageDemoText.DEMO_TEXT;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "C3";
    }
}
