package org.c3lang.intellij.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.c3lang.intellij.psi.ModuleName;
import org.c3lang.intellij.psi.ParamType;
import org.c3lang.intellij.psi.ShortType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * The indexed summary of a function definition – the worked example of a C3 stub.
 *
 * <p>A stub captures just enough about a declaration to answer cross-file questions (name, module,
 * receiver type, return type, parameter types) <em>without</em> keeping the whole parsed function in
 * memory. The IDE serializes these into its on-disk index and deserializes them on demand, which is
 * why opening one file does not force every other file in the project to be parsed.
 *
 * <p>The three constructors are the three ways a stub comes to exist:
 * <ul>
 *   <li>field-by-field – the canonical constructor the others delegate to;</li>
 *   <li>from a parsed {@link C3FuncDef} PSI element – used when indexing a freshly parsed file;</li>
 *   <li>from a {@link StubInputStream} – used when reloading a stub from the index.</li>
 * </ul>
 * {@link #serialize} writes the fields back out; the read/write order must stay in sync, and any
 * change here means bumping {@code C3FileElementType.STUB_VERSION}. The matching
 * {@link C3FuncDefElementType} ties this stub to its PSI element and registers it into the index.
 */
public class C3FuncDefStub extends StubBase<C3FuncDef>
{
	private final @NotNull String sourceFileName;
	private final @Nullable ModuleName module;
	private final @Nullable ShortType type;
	private final @NotNull FullyQualifiedName fqName;
	private final @Nullable ShortType returnType;
	private final @NotNull List<ParamType> parameterTypes;

	public C3FuncDefStub(
		@Nullable StubElement<?> parent,
		@Nullable IStubElementType<?, ?> elementType,
		@NotNull String sourceFileName,
		@Nullable ModuleName module,
		@Nullable ShortType type,
		@NotNull FullyQualifiedName fqName,
		@Nullable ShortType returnType,
		@NotNull List<ParamType> parameterTypes)
	{
		super(parent, elementType);
		this.sourceFileName = sourceFileName;
		this.module = module;
		this.type = type;
		this.fqName = fqName;
		this.returnType = returnType;
		this.parameterTypes = parameterTypes;
	}

	public C3FuncDefStub(
		@Nullable StubElement<?> parent,
		@Nullable IStubElementType<?, ?> elementType,
		@NotNull C3FuncDef psi)
	{
		this(
			parent,
			elementType,
			psi.getContainingFile().getName(),
			ModuleName.Companion.from(psi),
			psi.getFuncHeader().getFuncName().getType() != null
				? ShortType.Companion.toShortType(psi.getFuncHeader().getFuncName().getType())
				: null,
			FullyQualifiedName.Companion.from(psi.getFuncHeader(), ModuleName.Companion.from(psi)),
			psi.getFuncHeader().getOptionalType().getType() != null
				? ShortType.Companion.toShortType(psi.getFuncHeader().getOptionalType().getType())
				: null,
			ParamType.Companion.toParamTypeList(
				psi.getFnParameterList().getParameterList() != null
					? psi.getFnParameterList().getParameterList().getParamDeclList()
					: null)
		);
	}

	public C3FuncDefStub(
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
			ParamType.Companion.deserialize(dataStream)
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

	/** Writes the fields out in the same order the stream-reading constructor reads them back. */
	public void serialize(@NotNull StubOutputStream dataStream) throws IOException
	{
		dataStream.writeUTFFast(sourceFileName);
		StubStreamExtensions.writeNullableUTFFast(dataStream, module != null ? module.getValue() : null);
		StubStreamExtensions.writeNullableUTFFast(dataStream, type != null ? type.getFullName() : null);
		dataStream.writeUTFFast(fqName.getFullName());
		StubStreamExtensions.writeNullableUTFFast(dataStream, returnType != null ? returnType.getFullName() : null);
		ParamType.Companion.serialize(dataStream, parameterTypes);
	}
}
