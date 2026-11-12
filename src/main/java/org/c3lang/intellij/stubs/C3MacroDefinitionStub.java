package org.c3lang.intellij.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.c3lang.intellij.psi.C3AttributeNames;
import org.c3lang.intellij.psi.C3MacroDefinition;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.c3lang.intellij.psi.ModuleName;
import org.c3lang.intellij.psi.ParamType;
import org.c3lang.intellij.psi.ShortType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public class C3MacroDefinitionStub extends StubBase<C3MacroDefinition>
{
	private final @NotNull String sourceFileName;
	private final @Nullable ModuleName module;
	private final @Nullable ShortType type;
	private final @NotNull FullyQualifiedName fqName;
	private final @Nullable ShortType returnType;
	private final @NotNull List<ParamType> parameterTypes;
	private final boolean builtin;

	public C3MacroDefinitionStub(
		@Nullable StubElement<?> parent,
		@Nullable IStubElementType<?, ?> elementType,
		@NotNull String sourceFileName,
		@Nullable ModuleName module,
		@Nullable ShortType type,
		@NotNull FullyQualifiedName fqName,
		@Nullable ShortType returnType,
		@NotNull List<ParamType> parameterTypes,
		boolean builtin)
	{
		super(parent, elementType);
		this.sourceFileName = sourceFileName;
		this.module = module;
		this.type = type;
		this.fqName = fqName;
		this.returnType = returnType;
		this.parameterTypes = parameterTypes;
		this.builtin = builtin;
	}

	public C3MacroDefinitionStub(
		@Nullable StubElement<?> parent,
		@Nullable IStubElementType<?, ?> elementType,
		@NotNull C3MacroDefinition psi)
	{
		this(
			parent,
			elementType,
			psi.getContainingFile().getName(),
			ModuleName.Companion.from(psi),
			psi.getMacroHeader().getMacroName().getType() != null
				? ShortType.Companion.toShortType(psi.getMacroHeader().getMacroName().getType())
				: null,
			FullyQualifiedName.Companion.from(psi.getMacroHeader(), ModuleName.Companion.from(psi)),
			psi.getMacroHeader().getOptionalType() != null && psi.getMacroHeader().getOptionalType().getType() != null
				? ShortType.Companion.toShortType(psi.getMacroHeader().getOptionalType().getType())
				: null,
			ParamType.Companion.toParamTypeList(
				psi.getMacroParams().getParameterList() != null
					? psi.getMacroParams().getParameterList().getParamDeclList()
					: null),
			C3AttributeNames.has(psi.getAttributes(), "builtin")
		);
	}

	public C3MacroDefinitionStub(
		@Nullable StubElement<?> parent,
		@Nullable IStubElementType<?, ?> elementType,
		@NotNull StubInputStream dataStream) throws IOException
	{
		this(
			parent,
			elementType,
			dataStream.readUTFFast(),
			readModuleName(dataStream),
			readShortType(dataStream),
			FullyQualifiedName.Companion.parse(dataStream.readUTFFast()),
			readShortType(dataStream),
			ParamType.Companion.deserialize(dataStream),
			dataStream.readBoolean()
		);
	}

	private static @Nullable ModuleName readModuleName(@NotNull StubInputStream dataStream) throws IOException
	{
		String value = StubStreamExtensions.readNullableUTFFast(dataStream);
		return value != null ? new ModuleName(value) : null;
	}

	private static @Nullable ShortType readShortType(@NotNull StubInputStream dataStream) throws IOException
	{
		String value = StubStreamExtensions.readNullableUTFFast(dataStream);
		return value != null ? ShortType.Companion.parse(value) : null;
	}

	public @NotNull String getSourceFileName()
	{
		return sourceFileName;
	}

	public @Nullable ModuleName getModule()
	{
		return module;
	}

	public @Nullable ShortType getType()
	{
		return type;
	}

	public @NotNull FullyQualifiedName getFqName()
	{
		return fqName;
	}

	public @Nullable ShortType getReturnType()
	{
		return returnType;
	}

	public @NotNull List<ParamType> getParameterTypes()
	{
		return parameterTypes;
	}

	/** Declared {@code @builtin}: callable without a module path. */
	public boolean isBuiltin()
	{
		return builtin;
	}

	public void serialize(@NotNull StubOutputStream dataStream) throws IOException
	{
		dataStream.writeUTFFast(sourceFileName);
		StubStreamExtensions.writeNullableUTFFast(dataStream, module != null ? module.getValue() : null);
		StubStreamExtensions.writeNullableUTFFast(dataStream, type != null ? type.getFullName() : null);
		dataStream.writeUTFFast(fqName.getFullName());
		StubStreamExtensions.writeNullableUTFFast(dataStream, returnType != null ? returnType.getFullName() : null);
		ParamType.Companion.serialize(dataStream, parameterTypes);
		dataStream.writeBoolean(builtin);
	}
}
