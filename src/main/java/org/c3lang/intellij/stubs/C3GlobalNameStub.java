package org.c3lang.intellij.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.c3lang.intellij.psi.C3GlobalName;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.c3lang.intellij.psi.ModuleName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/** Stub of one declared global name: its fully qualified name, which the name index is keyed by. */
public class C3GlobalNameStub extends StubBase<C3GlobalName>
{
	private final @NotNull FullyQualifiedName name;

	public C3GlobalNameStub(
		@Nullable StubElement<?> parent,
		@Nullable IStubElementType<?, ?> elementType,
		@NotNull FullyQualifiedName name)
	{
		super(parent, elementType);
		this.name = name;
	}

	public C3GlobalNameStub(
		@NotNull StubElement<?> parent,
		@NotNull C3GlobalNameElementType elementType,
		@NotNull C3GlobalName psi)
	{
		// Runs during indexing: derived from the file text alone, never from the stub index.
		this(parent, elementType, new FullyQualifiedName(ModuleName.from(psi), nameOf(psi)));
	}

	public C3GlobalNameStub(
		@NotNull StubElement<?> parent,
		@NotNull C3GlobalNameElementType elementType,
		@NotNull StubInputStream dataStream) throws IOException
	{
		this(parent, elementType, FullyQualifiedName.Companion.parse(dataStream.readUTFFast()));
	}

	private static @NotNull String nameOf(@NotNull C3GlobalName psi)
	{
		String name = psi.getNameIdent();
		return name != null ? name : "";
	}

	public @NotNull FullyQualifiedName getName()
	{
		return name;
	}

	public void serialize(@NotNull StubOutputStream dataStream) throws IOException
	{
		dataStream.writeUTFFast(name.getFullName());
	}
}
