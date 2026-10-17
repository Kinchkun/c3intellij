package org.c3lang.intellij.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.c3lang.intellij.index.NameIndex;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3StubElementType;
import org.c3lang.intellij.psi.C3StubElementTypeFactory;
import org.c3lang.intellij.psi.impl.C3FuncDefImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * The element type that connects the {@code func_def} PSI element to its {@link C3FuncDefStub}.
 *
 * <p>For every kind of stub-backed declaration there is one of these. It is the bridge the platform
 * uses in both directions:
 * <ul>
 *   <li>{@link #createStub} – extract a stub from a parsed PSI element (when indexing a file);</li>
 *   <li>{@link #createPsi} – build a PSI element backed only by a stub (when no AST is loaded);</li>
 *   <li>{@link #serialize}/{@link #deserialize} – persist/restore the stub in the index;</li>
 *   <li>{@link #indexStub} – register the declaration's key so it can be found later.</li>
 * </ul>
 * Here {@link #indexStub} files the function under its fully-qualified name in {@link NameIndex},
 * which is exactly what {@code NameIndexService}/{@code C3FuncNameReference} query during resolution.
 */
public class C3FuncDefElementType extends C3StubElementType<C3FuncDefStub, C3FuncDef>
{
	private static final C3FuncDefElementType INSTANCE = new C3FuncDefElementType();

	private C3FuncDefElementType()
	{
		super(C3StubElementTypeFactory.FUNC_DEF);
	}

	public static C3FuncDefElementType getInstance()
	{
		return INSTANCE;
	}

	@Override
	public @NotNull C3FuncDef createPsi(@NotNull C3FuncDefStub stub)
	{
		return new C3FuncDefImpl(stub, this);
	}

	@Override
	public @NotNull C3FuncDefStub createStub(@NotNull C3FuncDef psi, @NotNull StubElement<? extends PsiElement> stubElement)
	{
		return new C3FuncDefStub(stubElement, this, psi);
	}

	@Override
	public void serialize(@NotNull C3FuncDefStub stub, @NotNull StubOutputStream dataStream) throws IOException
	{
		stub.serialize(dataStream);
	}

	@Override
	public @NotNull C3FuncDefStub deserialize(@NotNull StubInputStream stubInputStream, StubElement stubElement) throws IOException
	{
		return new C3FuncDefStub(stubElement, this, stubInputStream);
	}


	@Override
	public void indexStub(@NotNull C3FuncDefStub stub, @NotNull IndexSink sink)
	{
		// File this function under its full "module::name" key so lookups by name can find it.
		sink.occurrence(NameIndex.KEY, stub.getFqName().getFullName());
	}
}
