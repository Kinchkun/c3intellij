package org.c3lang.intellij.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.c3lang.intellij.intention.C3Interfaces;
import org.c3lang.intellij.psi.C3Type;
import org.c3lang.intellij.psi.C3TypeName;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.c3lang.intellij.psi.ModuleName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class C3TypeNameStub extends StubBase<C3TypeName>
{
	private final @NotNull FullyQualifiedName fqName;
	private final @Nullable ModuleName moduleName;
	private final @NotNull C3TypeEnum typeEnum;
	/** Simple names of the interfaces this type lists: {@code (Printable)}, or the parents of an interface. */
	private final @NotNull List<String> implementedInterfaces;

	public C3TypeNameStub(
		@Nullable StubElement<?> parent,
		@Nullable IStubElementType<?, ?> elementType,
		@NotNull FullyQualifiedName fqName,
		@Nullable ModuleName moduleName,
		@NotNull C3TypeEnum typeEnum,
		@NotNull List<String> implementedInterfaces)
	{
		super(parent, elementType);
		this.fqName = fqName;
		this.moduleName = moduleName;
		this.typeEnum = typeEnum;
		this.implementedInterfaces = implementedInterfaces;
	}

	public C3TypeNameStub(
		@Nullable StubElement<?> parent,
		@Nullable IStubElementType<?, ?> elementType,
		@NotNull StubInputStream dataStream) throws IOException
	{
		this(
			parent,
			elementType,
			FullyQualifiedName.Companion.parse(dataStream.readUTFFast()),
			readModuleName(dataStream),
			C3TypeEnum.valueOf(dataStream.readUTFFast()),
			readNames(dataStream)
		);
	}

	public C3TypeNameStub(
		@NotNull StubElement<?> parent,
		@NotNull C3TypeNameElementType elementType,
		@NotNull C3TypeName psi)
	{
		this(
			parent,
			elementType,
			FullyQualifiedName.Companion.from(psi, psi.getModuleName()),
			ModuleName.Companion.from(psi),
			C3TypeEnum.find(psi),
			collectImplementedInterfaces(psi)
		);
	}

	private static @Nullable ModuleName readModuleName(@NotNull StubInputStream dataStream) throws IOException
	{
		String value = StubStreamExtensions.readNullableUTFFast(dataStream);
		return value != null ? new ModuleName(value) : null;
	}

	/** Runs during indexing: must derive from the file text alone, never from the stub index. */
	private static @NotNull List<String> collectImplementedInterfaces(@NotNull C3TypeName psi)
	{
		List<String> names = new ArrayList<>();
		for (C3Type type : C3Interfaces.implementedInterfaceTypes(psi))
		{
			String name = C3Interfaces.simpleName(type);
			if (name != null && !names.contains(name)) names.add(name);
		}
		return names;
	}

	private static @NotNull List<String> readNames(@NotNull StubInputStream dataStream) throws IOException
	{
		int count = dataStream.readVarInt();
		List<String> names = new ArrayList<>(count);
		for (int index = 0; index < count; index++)
		{
			names.add(dataStream.readUTFFast());
		}
		return names;
	}

	public @NotNull List<String> getImplementedInterfaces()
	{
		return implementedInterfaces;
	}

	public @NotNull FullyQualifiedName getFqName()
	{
		return fqName;
	}

	public @Nullable ModuleName getModuleName()
	{
		return moduleName;
	}

	public @NotNull C3TypeEnum getTypeEnum()
	{
		return typeEnum;
	}

	public void serialize(@NotNull StubOutputStream dataStream) throws IOException
	{
		dataStream.writeUTFFast(fqName.getFullName());
		StubStreamExtensions.writeNullableUTFFast(dataStream, moduleName != null ? moduleName.getValue() : null);
		dataStream.writeUTFFast(typeEnum.name());
		dataStream.writeVarInt(implementedInterfaces.size());
		for (String name : implementedInterfaces)
		{
			dataStream.writeUTFFast(name);
		}
	}
}
