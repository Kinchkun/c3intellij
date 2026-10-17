package org.c3lang.intellij;

import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.c3lang.intellij.psi.C3StructDeclaration;
import org.c3lang.intellij.psi.C3StructMemberDeclaration;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.c3lang.intellij.psi.StructField;
import org.c3lang.intellij.stubs.C3StructDeclarationStub;
import org.c3lang.intellij.stubs.C3StructMemberDeclarationStub;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Struct stubs are built while indexing, i.e. in dumb mode, where the stub index must not be
 * queried (doing so fails indexing with "Failed to build stub tree ... IndexNotReadyException").
 * The field types recorded in the stubs therefore have to derive from the file text alone, and
 * must match what the AST-backed PSI computes in smart mode.
 */
public class C3StructStubTest extends BasePlatformTestCase
{
	private static final String BAR = "module foo::bar;\nstruct Bar { int x; }\n";

	public void testStubsBuiltInDumbModeMatchAst()
	{
		myFixture.addFileToProject("bar.c3", BAR);
		PsiFile file = myFixture.configureByText("baz.c3",
			"module app;\n" +
			"import foo::bar;\n" +
			"struct Other { int y; }\n" +
			"struct Baz { bar::Bar b; Other o; int n; }\n");

		C3StructDeclaration baz = structNamed(file, "Baz");
		List<StructField> fieldsFromAst = baz.getFields();
		FullyQualifiedName memberTypeFromAst = memberNamed(baz, "b").getStructPathType();

		assertEquals(List.of("foo::bar::Bar", "app::Other", "int"),
			fieldsFromAst.stream().map(f -> f.getType().getFullName()).toList());
		assertNotNull(memberTypeFromAst);
		assertEquals("foo::bar::Bar", memberTypeFromAst.getFullName());

		StubElement<?> root = buildStubTreeInDumbMode(file);

		C3StructDeclarationStub bazStub = findStubs(root, C3StructDeclarationStub.class).stream()
			.filter(s -> s.getTypeName().getName().equals("Baz")).findFirst().orElseThrow();
		assertEquals(fieldsFromAst, bazStub.getFields());

		C3StructMemberDeclarationStub memberStub = findStubs(root, C3StructMemberDeclarationStub.class).stream()
			.filter(s -> "b".equals(s.getStructPath()) && s.getStructType() != null
				&& s.getStructType().getName().equals("Baz"))
			.findFirst().orElseThrow();
		assertEquals(memberTypeFromAst, memberStub.getStructPathType());
	}

	/** Type suffixes must not get in the way of resolving the qualified base type. */
	public void testQualifiedPointerTypeResolvesViaImport()
	{
		myFixture.addFileToProject("bar.c3", BAR);
		PsiFile file = myFixture.configureByText("baz.c3",
			"module app;\nimport foo::bar;\nstruct Baz { bar::Bar* p; }\n");

		FullyQualifiedName type = memberNamed(structNamed(file, "Baz"), "p").getStructPathType();
		assertNotNull(type);
		assertEquals("foo::bar::Bar", type.getFullName());
	}

	/** A path may also name (the tail of) the current module. */
	public void testQualifiedTypeResolvesAgainstOwnModule()
	{
		PsiFile file = myFixture.configureByText("bar.c3",
			"module foo::bar;\nstruct Bar { int x; }\nstruct Baz { bar::Bar b; }\n");

		FullyQualifiedName type = memberNamed(structNamed(file, "Baz"), "b").getStructPathType();
		assertNotNull(type);
		assertEquals("foo::bar::Bar", type.getFullName());
	}

	private static @NotNull StubElement<?> buildStubTreeInDumbMode(@NotNull PsiFile file)
	{
		return DumbModeTestUtils.computeInDumbModeSynchronously(
			file.getProject(),
			() -> ((PsiFileImpl) file).calcStubTree().getRoot());
	}

	private static <T> @NotNull List<T> findStubs(@NotNull StubElement<?> stub, @NotNull Class<T> type)
	{
		List<T> result = new ArrayList<>();
		collectStubs(stub, type, result);
		return result;
	}

	private static <T> void collectStubs(@NotNull StubElement<?> stub, @NotNull Class<T> type, @NotNull List<T> result)
	{
		if (type.isInstance(stub)) result.add(type.cast(stub));
		for (StubElement<?> child : stub.getChildrenStubs())
		{
			collectStubs(child, type, result);
		}
	}

	private static @NotNull C3StructDeclaration structNamed(@NotNull PsiFile file, @NotNull String name)
	{
		return PsiTreeUtil.findChildrenOfType(file, C3StructDeclaration.class).stream()
			.filter(s -> s.getTypeName().getText().equals(name))
			.findFirst().orElseThrow();
	}

	private static @NotNull C3StructMemberDeclaration memberNamed(@NotNull C3StructDeclaration struct, @NotNull String name)
	{
		return PsiTreeUtil.findChildrenOfType(struct, C3StructMemberDeclaration.class).stream()
			.filter(m -> name.equals(m.getName()))
			.findFirst().orElseThrow();
	}
}
