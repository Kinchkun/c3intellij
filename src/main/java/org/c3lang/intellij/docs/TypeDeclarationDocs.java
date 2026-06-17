package org.c3lang.intellij.docs;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3EnumConstant;
import org.c3lang.intellij.psi.C3EnumDeclaration;
import org.c3lang.intellij.psi.C3Module;
import org.c3lang.intellij.psi.C3PsiElement;
import org.c3lang.intellij.psi.C3TypeName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Documentation for declarations that only need their signature line rendered:
 * structs, unions, enums, bitstructs, typedefs, aliases, attrdefs, constdefs,
 * faults, enum/constdef constants and modules.
 *
 * <p>The signature is reconstructed from the element text up to its body (the
 * first {@code {} or line break), which keeps a single implementation correct
 * across all of the above kinds without depending on kind specific getters.</p>
 */
public final class TypeDeclarationDocs
{
    private TypeDeclarationDocs()
    {
    }

    /** Generic signature + doc-comment + file rendering for a declaration. */
    public static @NotNull String generateDeclarationDoc(@NotNull C3PsiElement element)
    {
        return render(signatureLine(element), element, null);
    }

    /** Enum constant: shows the owning enum in addition to the signature. */
    public static @NotNull String generateEnumConstantDoc(@NotNull C3EnumConstant element)
    {
        C3EnumDeclaration owner = PsiTreeUtil.getParentOfType(element, C3EnumDeclaration.class);
        String enumName = owner != null ? typeNameText(owner.getTypeName()) : null;
        String extra = enumName != null ? section("Enum:", enumName) : null;
        return render(signatureLine(element), element, extra);
    }

    /** Module: rendered as {@code module <path>}. */
    public static @NotNull String generateModuleDoc(@NotNull C3Module element)
    {
        String path = element.getModulePath() != null ? element.getModulePath().getText().trim() : "";
        return render("module " + path, element, null);
    }

    private static @NotNull String render(@NotNull String signature, @NotNull PsiElement element, @Nullable String extraSection)
    {
        String docs = DocumentationUtils.findDocumentationComment(element.getParent() != null ? element.getParent() : element);
        String file = SymbolPresentationUtil.getFilePathPresentation(element.getContainingFile());

        StringBuilder builder = new StringBuilder();
        DocumentationUtils.appendDefinition(signature, element.getProject(), builder);
        DocumentationUtils.appendDescription(docs, element.getProject(), builder);
        builder.append(DocumentationMarkup.SECTIONS_START);

        if (extraSection != null)
        {
            builder.append(extraSection);
        }
        DocumentationUtils.appendFileSection(file, builder);
        builder.append(DocumentationMarkup.SECTIONS_END);
        return builder.toString();
    }

    private static @NotNull String section(@NotNull String header, @NotNull String value)
    {
        return DocumentationMarkup.SECTION_HEADER_START
            + header
            + DocumentationMarkup.SECTION_SEPARATOR
            + value
            + DocumentationMarkup.SECTION_END;
    }

    private static @Nullable String typeNameText(@Nullable C3TypeName typeName)
    {
        return typeName != null ? typeName.getName() : null;
    }

    /** The declaration header, i.e. everything before the body or the first line break. */
    private static @NotNull String signatureLine(@NotNull PsiElement element)
    {
        String text = element.getText();
        int end = text.length();
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '{' || c == '\n' || c == '\r')
            {
                end = i;
                break;
            }
        }
        String signature = text.substring(0, end).trim();
        // Drop a trailing end-of-statement marker for one liners such as faultdefs.
        if (signature.endsWith(";"))
        {
            signature = signature.substring(0, signature.length() - 1).trim();
        }
        return signature;
    }
}
