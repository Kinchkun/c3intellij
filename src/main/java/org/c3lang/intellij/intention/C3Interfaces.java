package org.c3lang.intellij.intention;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.index.NameIndexService;
import org.c3lang.intellij.psi.C3Attribute;
import org.c3lang.intellij.psi.C3AttributeName;
import org.c3lang.intellij.psi.C3Attributes;
import org.c3lang.intellij.psi.C3FullyQualifiedNamePsiElement;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3InterfaceDefinition;
import org.c3lang.intellij.psi.C3InterfaceImpl;
import org.c3lang.intellij.psi.C3StructDeclaration;
import org.c3lang.intellij.psi.C3Type;
import org.c3lang.intellij.psi.C3TypeName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Helpers for the interface-implementation inspection and quick fix. */
public final class C3Interfaces
{
	private C3Interfaces() {}

	/** Names of the interfaces a struct claims to implement, e.g. {@code (Printable, Other)}. */
	public static @NotNull List<String> interfaceNames(@NotNull C3StructDeclaration struct)
	{
		C3InterfaceImpl impl = struct.getInterfaceImpl();
		if (impl == null) return List.of();

		List<String> names = new ArrayList<>();
		String first = impl.getTypeName().getName();
		if (first != null) names.add(first);
		for (C3Type type : impl.getTypeList())
		{
			names.add(lastSegment(type.getText()));
		}
		return names;
	}

	public static @NotNull List<C3InterfaceDefinition> implementedInterfaces(@NotNull C3StructDeclaration struct)
	{
		List<C3InterfaceDefinition> result = new ArrayList<>();
		for (String name : interfaceNames(struct))
		{
			C3InterfaceDefinition definition = findInterface(name, struct.getProject());
			if (definition != null) result.add(definition);
		}
		return result;
	}

	/** Interface methods that the struct does not yet implement. */
	public static @NotNull List<C3FuncDef> missingMethods(@NotNull C3StructDeclaration struct)
	{
		String structName = struct.getTypeName().getName();
		if (structName == null) return List.of();

		Set<String> implemented = implementedMethodNames(struct, structName);

		List<C3FuncDef> missing = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (C3InterfaceDefinition iface : implementedInterfaces(struct))
		{
			for (C3FuncDef method : iface.getInterfaceBody().getFuncDefList())
			{
				// @optional methods do not need to be implemented.
				if (isOptional(method)) continue;
				String name = methodSimpleName(method);
				if (name == null || implemented.contains(name) || !seen.add(name)) continue;
				missing.add(method);
			}
		}
		return missing;
	}

	/** A {@code fn <ret> <Struct>.<method>(<params>) @dynamic { unreachable(); }} stub. */
	public static @NotNull String stubFor(@NotNull C3FuncDef interfaceMethod, @NotNull String structName)
	{
		String returnType = interfaceMethod.getFuncHeader().getOptionalType().getText();
		String params = interfaceMethod.getFnParameterList().getText();
		String name = methodSimpleName(interfaceMethod);
		// `unreachable()` is noreturn, so the stub compiles regardless of the return type.
		return "fn " + returnType + " " + structName + "." + name + params + " @dynamic\n{\n\tunreachable();\n}\n";
	}

	/** {@code @optional} interface methods the struct has not implemented yet. */
	public static @NotNull List<C3FuncDef> implementableOptionalMethods(@NotNull C3StructDeclaration struct)
	{
		String structName = struct.getTypeName().getName();
		if (structName == null) return List.of();

		Set<String> implemented = implementedMethodNames(struct, structName);

		List<C3FuncDef> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (C3InterfaceDefinition iface : implementedInterfaces(struct))
		{
			for (C3FuncDef method : iface.getInterfaceBody().getFuncDefList())
			{
				if (!isOptional(method)) continue;
				String name = methodSimpleName(method);
				if (name == null || implemented.contains(name) || !seen.add(name)) continue;
				result.add(method);
			}
		}
		return result;
	}

	/** Inserts {@code @dynamic} stubs for the given interface methods after the struct. */
	public static void insertStubs(@NotNull Project project, @NotNull C3StructDeclaration struct, @NotNull List<C3FuncDef> methods)
	{
		String structName = struct.getTypeName().getName();
		if (structName == null || methods.isEmpty()) return;

		StringBuilder stubs = new StringBuilder();
		for (C3FuncDef method : methods)
		{
			stubs.append("\n\n").append(stubFor(method, structName));
		}

		PsiFile file = struct.getContainingFile();
		Document document = PsiDocumentManager.getInstance(project).getDocument(file);
		if (document == null) return;

		document.insertString(struct.getTextRange().getEndOffset(), stubs.toString());
		PsiDocumentManager.getInstance(project).commitDocument(document);
	}

	/** True if an interface method is annotated {@code @optional} (need not be implemented). */
	public static boolean isOptional(@NotNull C3FuncDef method)
	{
		C3Attributes attributes = method.getAttributes();
		if (attributes == null) return false;
		for (C3Attribute attribute : attributes.getAttributeList())
		{
			C3AttributeName attributeName = attribute.getAttributeName();
			if (attributeName == null) continue;
			String text = attributeName.getText();
			if (text != null && text.replace("@", "").equals("optional")) return true;
		}
		return false;
	}

	public static @Nullable String methodSimpleName(@NotNull C3FuncDef method)
	{
		String text = method.getFuncHeader().getFuncName().getText();
		if (text == null) return null;
		int dot = text.lastIndexOf('.');
		return dot >= 0 ? text.substring(dot + 1) : text;
	}

	/**
	 * All methods callable on a value of the named interface type: the interface's own methods plus
	 * those inherited from parent interfaces ({@code interface Foo : Bar, Baz}). Interface methods
	 * are bare {@code func_def}s in the interface body (no {@code Type.} receiver), so unlike struct
	 * methods they are not found by a {@code Type.} name-prefix lookup.
	 */
	public static @NotNull List<C3FuncDef> interfaceMethods(@NotNull String name, @NotNull Project project)
	{
		C3InterfaceDefinition iface = findInterface(name, project);
		if (iface == null) return List.of();

		List<C3FuncDef> result = new ArrayList<>();
		collectInterfaceMethods(iface, project, result, new HashSet<>(), new HashSet<>());
		return result;
	}

	private static void collectInterfaceMethods(
		@NotNull C3InterfaceDefinition iface,
		@NotNull Project project,
		@NotNull List<C3FuncDef> result,
		@NotNull Set<String> seenMethods,
		@NotNull Set<String> seenInterfaces)
	{
		String ifaceName = iface.getTypeName().getName();
		if (ifaceName != null && !seenInterfaces.add(ifaceName)) return;

		for (C3FuncDef method : iface.getInterfaceBody().getFuncDefList())
		{
			String n = methodSimpleName(method);
			if (n != null && seenMethods.add(n)) result.add(method);
		}
		for (C3Type parent : iface.getTypeList())
		{
			C3InterfaceDefinition parentIface = findInterface(lastSegment(parent.getText()), project);
			if (parentIface != null)
			{
				collectInterfaceMethods(parentIface, project, result, seenMethods, seenInterfaces);
			}
		}
	}

	private static @Nullable C3InterfaceDefinition findInterface(@NotNull String name, @NotNull Project project)
	{
		for (C3FullyQualifiedNamePsiElement el : NameIndexService.INSTANCE.findByNameEndsWith(name, project))
		{
			if (el instanceof C3TypeName typeName
				&& name.equals(typeName.getName())
				&& typeName.getParent() instanceof C3InterfaceDefinition definition)
			{
				return definition;
			}
		}
		return null;
	}

	private static @NotNull Set<String> implementedMethodNames(@NotNull C3StructDeclaration struct, @NotNull String structName)
	{
		Set<String> names = new HashSet<>();
		PsiFile file = struct.getContainingFile();
		for (C3FuncDef func : PsiTreeUtil.findChildrenOfType(file, C3FuncDef.class))
		{
			String fqName = func.getFqName().getName();
			int dot = fqName.indexOf('.');
			if (dot > 0 && fqName.substring(0, dot).equals(structName))
			{
				names.add(fqName.substring(dot + 1));
			}
		}
		return names;
	}

	private static @NotNull String lastSegment(@NotNull String path)
	{
		int idx = path.lastIndexOf("::");
		return idx >= 0 ? path.substring(idx + 2) : path;
	}
}
