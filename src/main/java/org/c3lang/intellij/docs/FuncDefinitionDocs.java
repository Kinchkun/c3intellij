package org.c3lang.intellij.docs;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.project.Project;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3ParamDecl;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class FuncDefinitionDocs
{
    private FuncDefinitionDocs()
    {
    }

    public static @NotNull String generateFuncDefDoc(@NotNull C3FuncDef element)
    {
        String name = element.getFqName().getName();
        String type = element.getReturnType() != null ? element.getReturnType().getFullName() : "";
        String docs = DocumentationUtils.findDocumentationComment(element.getParent());
        String file = SymbolPresentationUtil.getFilePathPresentation(element.getContainingFile());
        var fnParameterList = element.getFnParameterList();
        String argsString = fnParameterList != null
            ? fnParameterList.getText().replaceAll("\\s+", " ").trim()
            : "()";
        var parameterList = fnParameterList != null ? fnParameterList.getParameterList() : null;
        List<String> args = parameterList != null
            ? parameterList.getParamDeclList().stream()
                .map(C3ParamDecl::getParameter)
                .filter(Objects::nonNull)
                .map(parameter -> parameter.getName())
                .filter(Objects::nonNull)
                .toList()
            : List.of();

        return renderFullDoc(file, name, type, argsString, args, docs, element.getProject());
    }

    private static @NotNull String renderFullDoc(
            @NotNull String file,
            @NotNull String name,
            @NotNull String type,
            @NotNull String argsString,
            @NotNull List<String> args,
            @NotNull String docs,
            @NotNull Project project)
    {
        StringBuilder builder = new StringBuilder();
        DocumentationUtils.appendDefinition("fn " + type + " " + name + argsString, project, builder);
        builder.append(DocumentationMarkup.SECTIONS_START);
        builder.append(DocumentationUtils.extractDescriptionTextFromDoc(docs)).append('\n');
        DocumentationUtils.appendParamsSection(docs, builder, args);
        DocumentationUtils.appendReturnSection(docs, builder);
        DocumentationUtils.appendFileSection(file, builder);
        builder.append(DocumentationMarkup.SECTIONS_END);
        return builder.toString();
    }
}
