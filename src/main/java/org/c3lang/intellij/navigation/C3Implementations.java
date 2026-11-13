package org.c3lang.intellij.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.c3lang.intellij.index.InterfaceImplementationIndex;
import org.c3lang.intellij.index.NameIndex;
import org.c3lang.intellij.intention.C3Interfaces;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3InterfaceBody;
import org.c3lang.intellij.psi.C3InterfaceDefinition;
import org.c3lang.intellij.psi.C3PsiElement;
import org.c3lang.intellij.psi.C3Type;
import org.c3lang.intellij.psi.C3TypeName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The implementation relation between interfaces and types, in both directions:
 * from an interface to the types that implement it and from an interface method to the methods
 * implementing it (Go to Implementation, the "is implemented" gutter icon), and from a method back
 * to the interface method it implements (the "implements" gutter icon).
 */
public final class C3Implementations
{
	private C3Implementations() {}

	/** The interface a type name declares ({@code interface Printable}), or null for any other type name. */
	public static @Nullable C3InterfaceDefinition interfaceOf(@NotNull C3TypeName typeName)
	{
		return astParent(typeName) instanceof C3InterfaceDefinition definition ? definition : null;
	}

	/** The interface whose body declares this function, or null for a function declared anywhere else. */
	public static @Nullable C3InterfaceDefinition declaringInterface(@NotNull C3FuncDef method)
	{
		if (!(astParent(method) instanceof C3InterfaceBody body)) return null;
		return astParent(body) instanceof C3InterfaceDefinition definition ? definition : null;
	}

	/**
	 * Every type that implements the interface: types listing it ({@code struct Foo (Printable)}),
	 * interfaces extending it ({@code interface Loud : Printable}) and, through those, their
	 * implementers.
	 */
	public static @NotNull List<C3TypeName> implementers(@NotNull C3TypeName interfaceName)
	{
		Set<C3TypeName> result = new LinkedHashSet<>();
		Deque<C3TypeName> pending = new ArrayDeque<>();
		Set<C3TypeName> visited = new HashSet<>();
		pending.add(interfaceName);
		visited.add(interfaceName);

		while (!pending.isEmpty())
		{
			C3TypeName current = pending.poll();
			for (C3TypeName implementer : directImplementers(current))
			{
				if (!visited.add(implementer)) continue;
				result.add(implementer);
				if (interfaceOf(implementer) != null) pending.add(implementer);
			}
		}
		return new ArrayList<>(result);
	}

	/** Whether at least one type implements the interface; cheaper than collecting them all. */
	public static boolean hasImplementers(@NotNull C3TypeName interfaceName)
	{
		return !directImplementers(interfaceName).isEmpty();
	}

	/**
	 * The methods implementing an interface method: {@code Foo.print} for each implementer
	 * {@code Foo} of the interface that declares {@code print}. A method is looked up in the module
	 * of its type.
	 */
	public static @NotNull List<C3FuncDef> implementingMethods(@NotNull C3FuncDef interfaceMethod)
	{
		C3InterfaceDefinition definition = declaringInterface(interfaceMethod);
		String methodName = C3Interfaces.methodSimpleName(interfaceMethod);
		if (definition == null || methodName == null) return List.of();

		Project project = interfaceMethod.getProject();
		List<C3FuncDef> result = new ArrayList<>();
		for (C3TypeName implementer : implementers(definition.getTypeName()))
		{
			if (interfaceOf(implementer) != null) continue;
			String key = implementer.getFqName().getFullName() + "." + methodName;
			for (C3PsiElement element : StubIndex.getElements(
				NameIndex.KEY, key, project, GlobalSearchScope.allScope(project), C3PsiElement.class))
			{
				if (element instanceof C3FuncDef method) result.add(method);
			}
		}
		return result;
	}

	/**
	 * The interface methods a method implements: for {@code fn void Foo.print(&self) @dynamic} the
	 * {@code print} of every interface {@code Foo} lists, including the interfaces those extend.
	 */
	public static @NotNull List<C3FuncDef> implementedInterfaceMethods(@NotNull C3FuncDef method)
	{
		C3Type receiver = method.getFuncHeader().getFuncName().getType();
		String methodName = C3Interfaces.methodSimpleName(method);
		if (receiver == null || methodName == null) return List.of();

		List<C3FuncDef> result = new ArrayList<>();
		Set<C3InterfaceDefinition> visited = new HashSet<>();
		for (C3TypeName receiverDeclaration : resolveTypeNames(receiver))
		{
			if (interfaceOf(receiverDeclaration) != null) continue;
			for (C3Type interfaceType : C3Interfaces.implementedInterfaceTypes(receiverDeclaration))
			{
				collectInterfaceMethods(interfaceType, methodName, result, visited);
			}
		}
		return result;
	}

	private static void collectInterfaceMethods(
		@NotNull C3Type interfaceType,
		@NotNull String methodName,
		@NotNull List<C3FuncDef> result,
		@NotNull Set<C3InterfaceDefinition> visited)
	{
		for (C3InterfaceDefinition definition : resolveInterfaces(interfaceType))
		{
			if (!visited.add(definition)) continue;
			for (C3FuncDef candidate : definition.getInterfaceBody().getFuncDefList())
			{
				if (methodName.equals(C3Interfaces.methodSimpleName(candidate))) result.add(candidate);
			}
			for (C3Type parent : definition.getTypeList())
			{
				collectInterfaceMethods(parent, methodName, result, visited);
			}
		}
	}

	/** The interfaces a type usage names. An unresolved name (a missing import) falls back to a lookup by simple name. */
	private static @NotNull List<C3InterfaceDefinition> resolveInterfaces(@NotNull C3Type type)
	{
		List<C3InterfaceDefinition> result = new ArrayList<>();
		List<C3TypeName> resolved = resolveTypeNames(type);
		for (C3TypeName typeName : resolved)
		{
			C3InterfaceDefinition definition = interfaceOf(typeName);
			if (definition != null) result.add(definition);
		}
		if (resolved.isEmpty())
		{
			String name = C3Interfaces.simpleName(type);
			C3InterfaceDefinition definition = name != null ? C3Interfaces.findInterface(name, type.getProject()) : null;
			if (definition != null) result.add(definition);
		}
		return result;
	}

	/** The types listing {@code interfaceName} directly, as implementers or as extending interfaces. */
	private static @NotNull List<C3TypeName> directImplementers(@NotNull C3TypeName interfaceName)
	{
		String name = interfaceName.getName();
		if (name == null) return List.of();

		Project project = interfaceName.getProject();
		List<C3TypeName> result = new ArrayList<>();
		for (C3TypeName candidate : StubIndex.getElements(
			InterfaceImplementationIndex.KEY, name, project, GlobalSearchScope.allScope(project), C3TypeName.class))
		{
			if (lists(candidate, interfaceName, name)) result.add(candidate);
		}
		return result;
	}

	/**
	 * Whether {@code candidate} lists this very interface and not a same-named one of another
	 * module. A name that does not resolve at all (a missing import) counts as a match.
	 */
	private static boolean lists(@NotNull C3TypeName candidate, @NotNull C3TypeName interfaceName, @NotNull String name)
	{
		for (C3Type type : C3Interfaces.implementedInterfaceTypes(candidate))
		{
			if (!name.equals(C3Interfaces.simpleName(type))) continue;

			List<C3TypeName> resolved = resolveTypeNames(type);
			if (resolved.isEmpty()) return true;
			for (C3TypeName target : resolved)
			{
				if (interfaceName.getManager().areElementsEquivalent(target, interfaceName)) return true;
			}
		}
		return false;
	}

	private static @NotNull List<C3TypeName> resolveTypeNames(@NotNull C3Type type)
	{
		PsiReference reference = type.getBaseType().getReference();
		if (reference == null) return List.of();

		List<C3TypeName> result = new ArrayList<>();
		if (reference instanceof PsiPolyVariantReference polyVariant)
		{
			for (ResolveResult resolveResult : polyVariant.multiResolve(false))
			{
				if (resolveResult.getElement() instanceof C3TypeName typeName) result.add(typeName);
			}
		}
		else if (reference.resolve() instanceof C3TypeName typeName)
		{
			result.add(typeName);
		}
		return result;
	}

	/** The parent in the syntax tree; the stub parent of a stub-backed element skips unstubbed declarations. */
	private static @Nullable PsiElement astParent(@NotNull PsiElement element)
	{
		ASTNode node = element.getNode();
		ASTNode parent = node != null ? node.getTreeParent() : null;
		return parent != null ? parent.getPsi() : null;
	}
}
