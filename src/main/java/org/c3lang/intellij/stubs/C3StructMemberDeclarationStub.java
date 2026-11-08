package org.c3lang.intellij.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.c3lang.intellij.psi.C3StructMemberDeclaration;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class C3StructMemberDeclarationStub extends StubBase<C3StructMemberDeclaration>
{
	private final @Nullable FullyQualifiedName structType;
	private final @Nullable String structPath;
	private final @Nullable FullyQualifiedName structPathType;
	private final @Nullable String fullPath;

	public C3StructMemberDeclarationStub(
		@Nullable StubElement<?> parent,
		@Nullable IStubElementType<?, ?> elementType,
		@Nullable FullyQualifiedName structType,
		@Nullable String structPath,
		@Nullable FullyQualifiedName structPathType)
	{
		super(parent, elementType);
		this.structType = structType;
		this.structPath = structPath;
		this.structPathType = structPathType;
		this.fullPath = buildFullPath(structType, structPath);
	}

	public C3StructMemberDeclarationStub(
		@Nullable StubElement<?> parent,
		@Nullable IStubElementType<?, ?> elementType,
		@NotNull C3StructMemberDeclaration psi)
	{
		// Runs during indexing: everything stored here must derive from the file text alone,
		// never from the stub index (see C3ModuleDefinitionMixinImpl#resolve(C3Type)).
		this(parent, elementType, psi.getStructType(), psi.getStructPath(), psi.getStructPathType());
	}

	public C3StructMemberDeclarationStub(
		@Nullable StubElement<?> parent,
		@Nullable IStubElementType<?, ?> elementType,
		@NotNull StubInputStream dataStream) throws IOException
	{
		this(
			parent,
			elementType,
			readFullyQualifiedName(dataStream),
			StubStreamExtensions.readNullableUTFFast(dataStream),
			readFullyQualifiedName(dataStream)
		);
	}

	private static @Nullable FullyQualifiedName readFullyQualifiedName(@NotNull StubInputStream dataStream) throws IOException
	{
		String value = StubStreamExtensions.readNullableUTFFast(dataStream);
		return value != null ? FullyQualifiedName.Companion.parse(value) : null;
	}

	private static @Nullable String buildFullPath(@Nullable FullyQualifiedName structType, @Nullable String structPath)
	{
		List<String> parts = new ArrayList<>(2);
		if (structType != null) parts.add(structType.getFullName());
		if (structPath != null) parts.add(structPath);
		return parts.isEmpty() ? null : String.join(".", parts);
	}

	public @Nullable FullyQualifiedName getStructType()
	{
		return structType;
	}

	public @Nullable String getStructPath()
	{
		return structPath;
	}

	public @Nullable FullyQualifiedName getStructPathType()
	{
		return structPathType;
	}

	public @Nullable String getFullPath()
	{
		return fullPath;
	}

	/**
	 * The index keys of this member, one per declared name: {@code int w, h;} in {@code Size}
	 * declares {@code Size.w} and {@code Size.h}.
	 */
	public @NotNull List<String> getFullPaths()
	{
		if (structType == null) return List.of();
		List<String> keys = new ArrayList<>();
		for (String path : memberPaths(structPath))
		{
			keys.add(structType.getFullName() + "." + path);
		}
		return keys;
	}

	/**
	 * Expands a struct path whose last segment lists several names ({@code inner.w, h}) into one
	 * path per name ({@code inner.w}, {@code inner.h}); a single-name path stays as it is.
	 */
	public static @NotNull List<String> memberPaths(@Nullable String structPath)
	{
		if (structPath == null) return List.of();
		int dot = structPath.lastIndexOf('.');
		String prefix = dot >= 0 ? structPath.substring(0, dot + 1) : "";
		List<String> paths = new ArrayList<>();
		for (String name : structPath.substring(dot + 1).split(","))
		{
			String trimmed = name.trim();
			if (!trimmed.isEmpty()) paths.add(prefix + trimmed);
		}
		return paths;
	}

	public void serialize(@NotNull StubOutputStream stream) throws IOException
	{
		StubStreamExtensions.writeNullableUTFFast(stream, structType != null ? structType.getFullName() : null);
		StubStreamExtensions.writeNullableUTFFast(stream, structPath);
		StubStreamExtensions.writeNullableUTFFast(stream, structPathType != null ? structPathType.getFullName() : null);
	}
}
