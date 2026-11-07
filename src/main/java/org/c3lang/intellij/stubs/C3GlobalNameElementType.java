package org.c3lang.intellij.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.c3lang.intellij.index.NameIndex;
import org.c3lang.intellij.psi.C3GlobalName;
import org.c3lang.intellij.psi.C3StubElementType;
import org.c3lang.intellij.psi.C3StubElementTypeFactory;
import org.c3lang.intellij.psi.impl.C3GlobalNameImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class C3GlobalNameElementType extends C3StubElementType<C3GlobalNameStub, C3GlobalName>
{
	private static final C3GlobalNameElementType INSTANCE = new C3GlobalNameElementType();

	private C3GlobalNameElementType()
	{
		super(C3StubElementTypeFactory.GLOBAL_NAME);
	}

	public static C3GlobalNameElementType getInstance()
	{
		return INSTANCE;
	}

	@Override
	public @NotNull C3GlobalName createPsi(@NotNull C3GlobalNameStub stub)
	{
		return new C3GlobalNameImpl(stub, this);
	}

	@Override
	public @NotNull C3GlobalNameStub createStub(@NotNull C3GlobalName psi, @NotNull StubElement<? extends PsiElement> stubElement)
	{
		return new C3GlobalNameStub(stubElement, this, psi);
	}

	@Override
	public void serialize(@NotNull C3GlobalNameStub stub, @NotNull StubOutputStream dataStream) throws IOException
	{
		stub.serialize(dataStream);
	}

	@Override
	public @NotNull C3GlobalNameStub deserialize(@NotNull StubInputStream dataStream, StubElement stubElement) throws IOException
	{
		return new C3GlobalNameStub(stubElement, this, dataStream);
	}

	@Override
	public void indexStub(@NotNull C3GlobalNameStub stub, @NotNull IndexSink sink)
	{
		sink.occurrence(NameIndex.KEY, stub.getName().getFullName());
	}
}
