package org.c3lang.intellij.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.c3lang.intellij.psi.C3AttributeNames;
import org.c3lang.intellij.psi.C3FaultDefinition;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.c3lang.intellij.psi.ModuleName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class C3FaultDefinitionStub extends StubBase<C3FaultDefinition>
{
	private final @NotNull FullyQualifiedName name;
	private final boolean builtin;

	public C3FaultDefinitionStub(
		@Nullable StubElement<?> parent,
		@Nullable IStubElementType<?, ?> elementType,
		@NotNull FullyQualifiedName name,
		boolean builtin)
	{
		super(parent, elementType);
		this.name = name;
		this.builtin = builtin;
	}

	public C3FaultDefinitionStub(
		@NotNull StubElement<?> parent,
		@NotNull C3FaultDefinitionElementType elementType,
		@NotNull C3FaultDefinition psi)
	{
		this(parent, elementType, FullyQualifiedName.Companion.from(psi, ModuleName.Companion.from(psi)),
			C3AttributeNames.has(psi.getAttributes(), "builtin"));
	}

	public C3FaultDefinitionStub(
		@NotNull StubElement<?> parent,
		@NotNull C3FaultDefinitionElementType elementType,
		@NotNull StubInputStream dataStream) throws IOException
	{
		this(parent, elementType, FullyQualifiedName.Companion.parse(dataStream.readUTFFast()), dataStream.readBoolean());
	}

	public @NotNull FullyQualifiedName getName()
	{
		return name;
	}

	/** Declared {@code @builtin}: usable without a module path. */
	public boolean isBuiltin()
	{
		return builtin;
	}

	public void serialize(@NotNull StubOutputStream dataStream) throws IOException
	{
		dataStream.writeUTFFast(name.getFullName());
		dataStream.writeBoolean(builtin);
	}
}
