package org.c3lang.intellij;

import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.DefaultStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IStubFileElementType;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.stubs.C3FileStub;
import org.jetbrains.annotations.NotNull;

/**
 * The element type of a C3 file's root node, and the entry point for building its <em>stub tree</em>.
 *
 * <p>A stub tree is a compact, serializable summary of a file's top-level declarations (modules,
 * functions, types, constants...) that the IDE stores in its index. It lets features such as
 * go-to-declaration and completion find symbols across the whole project <em>without</em> re-parsing
 * every file – they read the cheap stubs instead. See the {@code stubs/} and {@code index/} packages.
 *
 * <p>This class is an {@link IStubFileElementType}: the platform asks it for a {@link StubBuilder},
 * which walks a freshly parsed file and produces the stub for its root ({@link C3FileStub}). The
 * per-declaration stubs below the root are produced by the individual element types (e.g.
 * {@code stubs/C3FuncDefElementType}).
 *
 * <p>Referenced from {@link C3ParserDefinition#getFileNodeType()}.
 */
public class C3FileElementType extends IStubFileElementType<C3FileStub> {
    public static final C3FileElementType INSTANCE = new C3FileElementType();

    // Bump on any change affecting the stub layout/PSI so the IDE rebuilds the index on update
    // instead of reusing a stale one (a stale index logs "Stub index points to a file ... without
    // indexed stub tree" and breaks cross-file resolution/highlighting until Invalidate Caches).
    public static final int STUB_VERSION = 3;

    public C3FileElementType() {
        super("c3.FILE", C3Language.INSTANCE);
    }

    @Override
    public int getStubVersion() {
        return STUB_VERSION;
    }

    /** Supplies the builder that creates the stub for a parsed file's root node. */
    @Override
    public StubBuilder getBuilder() {
        return new DefaultStubBuilder() {
            @Override
            protected @NotNull StubElement<?> createStubForFile(@NotNull PsiFile file) {
                if (file instanceof C3File) {
                    return new C3FileStub((C3File) file);
                }
                return super.createStubForFile(file);
            }
        };
    }

//    @Override
//    public @NonNls @NotNull String getExternalId() {
//        return "c3.FILE";
//    }
//    @Override
//    public @NotNull C3FileStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
//        return super.deserialize(dataStream, parentStub);
//    }
//
//    @Override
//    public void serialize(@NotNull C3FileStub stub, @NotNull StubOutputStream dataStream) throws IOException {
//        super.serialize(stub, dataStream);
//    }
}
