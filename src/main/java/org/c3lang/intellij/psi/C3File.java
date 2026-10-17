package org.c3lang.intellij.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.c3lang.intellij.C3Icons;
import org.c3lang.intellij.C3Language;
import org.c3lang.intellij.C3SourceFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * The PSI node representing a whole C3 source file – the root of the tree for one file.
 *
 * <p>PSI ("Program Structure Interface") is IntelliJ's typed, navigable model of source code. Every
 * other C3 PSI element (modules, functions, statements...) lives underneath a {@code C3File}. By
 * extending {@link PsiFileBase} we get the standard file behaviour for free; we only need to declare
 * which language and file type we are.
 *
 * <p>Created by {@link org.c3lang.intellij.C3ParserDefinition#createFile}.
 *
 * @author Christoffer Lerno
 */
public class C3File extends PsiFileBase {
    public C3File(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, C3Language.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return C3SourceFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "C3 File";
    }

    @Override
    public @Nullable Icon getIcon(int flags) {
        return C3Icons.FILE;
    }
}
