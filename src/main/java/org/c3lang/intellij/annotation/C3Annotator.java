package org.c3lang.intellij.annotation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.c3lang.intellij.C3ParserDefinition;
import org.c3lang.intellij.psi.C3CallExpr;
import org.jetbrains.annotations.NotNull;

/**
 * The registered C3 {@link Annotator} – the entry point for fast, per-element highlighting/validation.
 *
 * <p>An annotator runs continuously as you type: the platform calls {@link #annotate} for every PSI
 * element, and we attach extra colours and inline errors/warnings via the {@link AnnotationHolder}.
 * (Contrast with a {@code LocalInspectionTool}, which is heavier, runs as a separate pass and can
 * offer quick fixes – see {@code intention/C3DuplicateVariableInspection}.)
 *
 * <p>This class is just the thin entry point declared in {@code plugin.xml}
 * ({@code <annotator language="C3" .../>}). It dispatches doc-comment highlighting to
 * {@link DocCommentAnnotator} and the bulk of the work (semantic colouring of types, calls, escapes,
 * alias-naming checks...) to the {@link org.c3lang.intellij.C3Annotator} singleton.
 */
public class C3Annotator implements Annotator
{
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder)
    {
        if (element instanceof PsiComment comment)
        {
            if (comment.getNode().getElementType() == C3ParserDefinition.DOC_COMMENT)
            {
                DocCommentAnnotator.annotateDocComment(comment, holder);
            }
        }

        // Delegate semantic colouring + validation to the main annotator implementation.
        org.c3lang.intellij.C3Annotator.INSTANCE.annotate(element, holder);
    }

    @SuppressWarnings("unused")
    private void annotateMissingCallables(@NotNull C3CallExpr callExpr, @NotNull AnnotationHolder holder)
    {
//        C3PathIdentExpr pathIdentExpr = callExpr.getExpr() instanceof C3PathIdentExpr expr ? expr : null;
//        if (pathIdentExpr == null) return;
//        PsiElement nameIdentElement = pathIdentExpr.getPathIdent().getNameIdentElement();
//        if (nameIdentElement == null) return;
//        String callName = nameIdentElement.getText();
//
//        List<FullyQualifiedName> resolved = callExpr.getModuleDefinition().resolve(pathIdentExpr);
//        List<C3CallablePsiElement> elements = resolved.stream()
//                .flatMap(it -> NameIndexService.findByName(it, callExpr.getProject()).stream())
//                .toList();
//
//        if (elements.isEmpty())
//        {
//            error(holder, callName + " not found", nameIdentElement);
//        }
//        else if (elements.size() > 1)
//        {
//            warning(holder, "Warning Too many " + callName + " found.", nameIdentElement);
//        }
    }

    @SuppressWarnings("unused")
    private static void error(@NotNull AnnotationHolder holder, @NotNull String message, @NotNull PsiElement element)
    {
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(element.getTextRange()).create();
    }

    @SuppressWarnings("unused")
    private static void warning(@NotNull AnnotationHolder holder, @NotNull String message, @NotNull PsiElement element)
    {
        holder.newAnnotation(HighlightSeverity.WARNING, message).range(element.getTextRange()).create();
    }

    @SuppressWarnings("unused")
    private static void weakWarning(@NotNull AnnotationHolder holder, @NotNull String message, @NotNull PsiElement element)
    {
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING, message).range(element.getTextRange()).create();
    }

    @SuppressWarnings("unused")
    private static void info(@NotNull AnnotationHolder holder, @NotNull String message, @NotNull PsiElement element)
    {
        holder.newAnnotation(HighlightSeverity.INFORMATION, message).range(element.getTextRange()).create();
    }
}
