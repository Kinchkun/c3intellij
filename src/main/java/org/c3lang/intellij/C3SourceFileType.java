package org.c3lang.intellij;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.OSFileIdeAssociation;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * The file type for C3 source files ({@code .c3}, {@code .c3t}).
 *
 * <p>A {@link LanguageFileType} is how the IDE answers "what kind of file is this, and what language
 * does it contain?". It supplies the name, description, icon and default extension shown in the UI.
 *
 * <p>The link between extensions and this type lives in {@code plugin.xml}:
 * <pre>{@code
 * <fileType name="C3 File" implementationClass="...C3SourceFileType"
 *           fieldName="INSTANCE" language="C3" extensions="c3;c3t"/>
 * }</pre>
 * Because it declares {@code language="C3"}, opening a {@code .c3} file tells the platform to drive
 * it with the C3 lexer/parser (see {@link C3ParserDefinition}).
 *
 * <p>{@link OSFileIdeAssociation} additionally lets the OS associate these files with the IDE.
 *
 * @author Christoffer Lerno
 */
public class C3SourceFileType extends LanguageFileType implements OSFileIdeAssociation {
    /** Single shared instance, referenced by {@code fieldName="INSTANCE"} in plugin.xml. */
    public static final C3SourceFileType INSTANCE = new C3SourceFileType();

    private C3SourceFileType() {
        super(C3Language.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "C3 File";
    }

    @Override
    @NotNull
    public String getDescription() {
        return "C3 source file";
    }

    @Override
    @NotNull
    public String getDefaultExtension() {
        return "c3";
    }

    @Override
    @NotNull
    public Icon getIcon() {
        return C3Icons.FILE;
    }

}
