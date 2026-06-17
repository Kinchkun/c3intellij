package org.c3lang.intellij.docs;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import org.c3lang.intellij.C3Language;
import org.c3lang.intellij.C3ParserDefinition;
import org.c3lang.intellij.psi.C3DefaultModuleSection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DocumentationUtils
{
    private static final Pattern PARAM_PATTERN = Pattern.compile(
        "@param\\s+((\\[(in|&in|out|&out|inout|&inout)])\\s+)?(\\w+)(\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|`((?:[^`\\\\]|\\\\.)*)`))?"
    );
    private static final Pattern RETURN_PATTERN = Pattern.compile("@return\\s+(\"[\\w\\s]+\")?");

    private DocumentationUtils()
    {
    }

    public static @NotNull String findDocumentationComment(@NotNull PsiElement element)
    {
        PsiElement prev = element.getParent() != null
            && element.getParent().getParent() instanceof C3DefaultModuleSection
            ? element.getParent().getParent().getPrevSibling()
            : element.getParent() != null ? element.getParent().getPrevSibling() : null;

        while (prev instanceof PsiWhiteSpace)
        {
            prev = prev.getPrevSibling();
        }

        if (prev == null) return "";

        StringBuilder builder = new StringBuilder();
        while (prev != null && prev.getNode().getElementType() == C3ParserDefinition.DOC_COMMENT)
        {
            builder.append(prev.getText()).append('\n');
            prev = prev.getPrevSibling();
        }

        return builder.toString().replace("<*", "").replace("*>", "");
    }

    static @NotNull String applyHtmlStyles(@NotNull String text, TextAttributes attributes)
    {
        if (attributes == null) return HtmlChunk.text(text).toString();

        String color = attributes.getForegroundColor() != null
            ? String.format("#%06x", attributes.getForegroundColor().getRGB() & 0xFFFFFF)
            : null;
        String style = color != null ? "color:" + color : "";

        return "<span style=\"" + style + "\">" + HtmlChunk.text(text) + "</span>";
    }

    static void appendDefinition(@NotNull String fmt, @NotNull Project project, @NotNull StringBuilder builderIn)
    {
        builderIn.append(DocumentationMarkup.DEFINITION_START);

        var highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(C3Language.INSTANCE, project, null);
        var tokens = highlighter.getHighlightingLexer();
        tokens.start(fmt);

        var scheme = EditorColorsManager.getInstance().getGlobalScheme();
        StringBuilder builder = new StringBuilder();

        while (tokens.getTokenType() != null)
        {
            String tokenText = fmt.substring(tokens.getTokenStart(), tokens.getTokenEnd());
            var attrKeys = highlighter.getTokenHighlights(tokens.getTokenType());
            TextAttributes attributes = null;
            for (var attrKey : attrKeys)
            {
                attributes = scheme.getAttributes(attrKey);
                if (attributes != null) break;
            }
            builder.append(applyHtmlStyles(tokenText, attributes));
            tokens.advance();
        }

        builderIn.append(builder);
        builderIn.append(DocumentationMarkup.DEFINITION_END);
    }

    static void appendFileSection(@NotNull String file, @NotNull StringBuilder builder)
    {
        builder.append(DocumentationMarkup.SECTION_HEADER_START);
        builder.append("File:");
        builder.append(DocumentationMarkup.SECTION_SEPARATOR);
        builder.append(file);
        builder.append(DocumentationMarkup.SECTION_END);
    }

    static void appendParamsSection(@NotNull String docs, @NotNull StringBuilder builder, @NotNull List<String> args)
    {
        Map<String, ParamDoc> params = extractParamsFromDoc(docs, args);
        if (params.isEmpty()) return;

        builder.append(DocumentationMarkup.SECTION_HEADER_START);
        builder.append("Params:");
        builder.append(DocumentationMarkup.SECTION_SEPARATOR);
        builder.append(formatParamSection(params));
        builder.append(DocumentationMarkup.SECTION_END);
    }

    static void appendReturnSection(@NotNull String docs, @NotNull StringBuilder builder)
    {
        String returnString = extractReturnFromDoc(docs);
        if (returnString.isEmpty()) return;

        builder.append(DocumentationMarkup.SECTION_HEADER_START);
        builder.append("Returns:");
        builder.append(DocumentationMarkup.SECTION_SEPARATOR);
        builder.append(formatReturnSection(returnString));
        builder.append(DocumentationMarkup.SECTION_END);
    }

    static @NotNull String formatReturnSection(@NotNull String desc)
    {
        return desc.replace("\"", "");
    }

    static @NotNull String formatParamSection(@NotNull Map<String, ParamDoc> params)
    {
        if (params.isEmpty()) return "";

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, ParamDoc> entry : params.entrySet())
        {
            String name = entry.getKey();
            String description = entry.getValue().description();
            String contract = entry.getValue().contract();
            String safeDescription = !description.isBlank()
                ? " - " + dropFirstAndLast(description)
                : "";
            String safeContract = !contract.isBlank()
                ? "<span style=\"color:#ffccff;\"><i>" + contract + "</i></span>"
                : "";
            builder.append("<p><code>")
                .append(safeContract)
                .append(name)
                .append("</code>")
                .append(safeDescription)
                .append("</p>");
        }

        return builder.toString();
    }

    static @NotNull Map<String, ParamDoc> extractParamsFromDoc(@NotNull String docComment, @NotNull List<String> args)
    {
        LinkedHashMap<String, ParamDoc> result = new LinkedHashMap<>();
        Matcher matcher = PARAM_PATTERN.matcher(docComment);
        while (matcher.find())
        {
            String contract = valueOrEmpty(matcher.group(1));
            String name = valueOrEmpty(matcher.group(4));
            String description = valueOrEmpty(matcher.group(6));

            if (args.contains(name))
            {
                result.put(name, new ParamDoc(description, contract));
            }
        }

        LinkedHashMap<String, ParamDoc> reversed = new LinkedHashMap<>();
        List<Map.Entry<String, ParamDoc>> entries = new ArrayList<>(result.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--)
        {
            Map.Entry<String, ParamDoc> entry = entries.get(i);
            reversed.put(entry.getKey(), entry.getValue());
        }
        return reversed;
    }

    static @NotNull String extractReturnFromDoc(@NotNull String docComment)
    {
        Matcher matcher = RETURN_PATTERN.matcher(docComment);
        if (matcher.find())
        {
            String desc = matcher.group(1);
            return desc != null ? desc.trim() : "";
        }
        return "";
    }

    /**
     * Appends the rendered description of a doc comment to {@code builder}.
     *
     * <p>The free-text part of the comment (everything that is not a {@code @param} /
     * {@code @return} / ... contract line) is treated as Markdown and converted to HTML.
     * Fenced code blocks are syntax highlighted; a {@code ```c3} block — or a bare
     * {@code ```} block, which falls back to the default language — is highlighted as C3.</p>
     */
    static void appendDescription(@NotNull String docComment, @NotNull Project project, @NotNull StringBuilder builder)
    {
        String markdown = extractDescriptionTextFromDoc(docComment);
        if (markdown.isBlank()) return;
        builder.append(DocumentationMarkup.CONTENT_START);
        builder.append(DocMarkdownToHtmlConverter.convert(project, markdown, C3Language.INSTANCE));
        builder.append(DocumentationMarkup.CONTENT_END);
    }

    /**
     * Extracts the free-text (Markdown) part of a doc comment, preserving line order, blank
     * lines and relative indentation so that Markdown structure (paragraphs, lists, fenced
     * code) survives. Contract lines ({@code @param}, {@code @return}, ...) are dropped since
     * they are rendered in their own sections, but {@code @}-prefixed lines inside a fenced
     * code block are kept verbatim.
     */
    static @NotNull String extractDescriptionTextFromDoc(@NotNull String docComment)
    {
        List<String> kept = new ArrayList<>();
        boolean inFence = false;
        for (String line : docComment.split("\n", -1))
        {
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~"))
            {
                inFence = !inFence;
                kept.add(stripTrailing(line));
                continue;
            }
            // Contract annotations are rendered separately; skip them unless inside a code fence.
            if (!inFence && trimmed.startsWith("@")) continue;
            kept.add(stripTrailing(line));
        }

        // Drop blank lines at the edges so the rendered block has no leading/trailing gap.
        int start = 0;
        int end = kept.size();
        while (start < end && kept.get(start).isBlank()) start++;
        while (end > start && kept.get(end - 1).isBlank()) end--;
        if (start >= end) return "";

        // Strip the common leading indentation shared by every non-blank line (doc comments
        // are typically indented inside `<* ... *>`), which would otherwise be read as a
        // Markdown indented code block.
        int indent = Integer.MAX_VALUE;
        for (int i = start; i < end; i++)
        {
            String line = kept.get(i);
            if (line.isBlank()) continue;
            indent = Math.min(indent, leadingWhitespace(line));
        }
        if (indent == Integer.MAX_VALUE) indent = 0;

        StringBuilder builder = new StringBuilder();
        for (int i = start; i < end; i++)
        {
            String line = kept.get(i);
            builder.append(line.length() >= indent ? line.substring(indent) : line).append('\n');
        }
        return builder.toString();
    }

    private static int leadingWhitespace(@NotNull String line)
    {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return i;
    }

    private static @NotNull String stripTrailing(@NotNull String line)
    {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) end--;
        return line.substring(0, end);
    }

    private static @NotNull String valueOrEmpty(String value)
    {
        return value != null ? value : "";
    }

    private static @NotNull String dropFirstAndLast(@NotNull String value)
    {
        if (value.length() <= 1) return "";
        return value.substring(1, value.length() - 1);
    }

    record ParamDoc(@NotNull String description, @NotNull String contract)
    {
    }
}
