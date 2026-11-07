package org.c3lang.intellij.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.c3lang.intellij.index.NameIndex;
import org.c3lang.intellij.psi.C3AliasName;
import org.c3lang.intellij.psi.C3StubElementType;
import org.c3lang.intellij.psi.C3StubElementTypeFactory;
import org.c3lang.intellij.psi.impl.C3AliasNameImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class C3AliasNameElementType extends C3StubElementType<C3AliasNameStub, C3AliasName>
{
	private static final C3AliasNameElementType INSTANCE = new C3AliasNameElementType();

	private C3AliasNameElementType()
	{
		super(C3StubElementTypeFactory.ALIAS_NAME);
	}

	public static C3AliasNameElementType getInstance()
	{
		return INSTANCE;
	}

	@Override
	public @NotNull C3AliasName createPsi(@NotNull C3AliasNameStub stub)
	{
		return new C3AliasNameImpl(stub, this);
	}

	@Override
	public @NotNull C3AliasNameStub createStub(@NotNull C3AliasName psi, @NotNull StubElement<? extends PsiElement> stubElement)
	{
		return new C3AliasNameStub(stubElement, this, psi);
	}

	@Override
	public void serialize(@NotNull C3AliasNameStub stub, @NotNull StubOutputStream dataStream) throws IOException
	{
		stub.serialize(dataStream);
	}

	@Override
	public @NotNull C3AliasNameStub deserialize(@NotNull StubInputStream dataStream, StubElement stubElement) throws IOException
	{
		return new C3AliasNameStub(stubElement, this, dataStream);
	}

	@Override
	public void indexStub(@NotNull C3AliasNameStub stub, @NotNull IndexSink sink)
	{
		sink.occurrence(NameIndex.KEY, stub.getName().getFullName());
	}
}
