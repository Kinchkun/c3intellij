package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.index.NameIndexService;
import org.c3lang.intellij.psi.C3AliasDecl;
import org.c3lang.intellij.psi.C3AliasName;
import org.c3lang.intellij.psi.C3AliasTypeDecl;
import org.c3lang.intellij.psi.C3Attribute;
import org.c3lang.intellij.psi.C3Attributes;
import org.c3lang.intellij.psi.C3ConstDeclarationStmt;
import org.c3lang.intellij.psi.C3EnumDeclaration;
import org.c3lang.intellij.psi.C3FaultDefinition;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3FullyQualifiedNamePsiElement;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3FuncDefinition;
import org.c3lang.intellij.psi.C3GlobalDecl;
import org.c3lang.intellij.psi.C3InterfaceDefinition;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3LocalDeclarationStmt;
import org.c3lang.intellij.psi.C3Parameter;
import org.c3lang.intellij.psi.C3StructDeclaration;
import org.c3lang.intellij.psi.C3Type;
import org.c3lang.intellij.psi.C3TypeName;
import org.c3lang.intellij.psi.C3TypedefDecl;
import org.c3lang.intellij.psi.C3Types;
import org.c3lang.intellij.psi.C3Visitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Greys out declarations that have no usages — structs/unions, interfaces, enums, typedefs, aliases,
 * faults, functions, methods, global variables, constants, function parameters and local variables
 * — and offers to delete them.
 *
 * <p>Most usages are located with {@link ReferencesSearch}: C3 references resolve to the
 * declaration's identity element (a {@code C3TypeName} for types, the {@code C3FuncDef} for
 * functions, the {@code C3Parameter} for parameters, the declaration itself for constants and
 * faults), so a declaration with no resolving reference is unused. Globals, plain aliases and local
 * variables are not covered precisely by the resolve infrastructure (the local resolver only sees a
 * single block), so for those we fall back to searching for the identifier in code within the
 * relevant scope — which can only ever under-report, never grey out something that is used.</p>
 *
 * <p>To avoid false positives, entry points and externally-visible declarations are skipped: the
 * {@code main} function, anything carrying an attribute such as {@code @export}, {@code @cname} or
 * {@code @init}, and methods (plus their parameters) that satisfy an interface the receiver
 * implements — those are part of a contract and may be invoked only through dynamic dispatch.</p>
 */
public final class C3UnusedDeclarationInspection extends LocalInspectionTool
{
	/** Attributes that imply a declaration is reachable outside the project's own call graph. */
	private static final Set<String> ENTRY_POINT_ATTRIBUTES = Set.of(
		"export", "extern", "cname", "link", "section", "test", "benchmark", "dynamic", "if",
		"init", "finalizer", "builtin", "operator", "naked", "used", "weak");

	@Override
	public @NotNull String getDisplayName()
	{
		return "Unused declaration";
	}

	@Override
	public @NotNull String getGroupDisplayName()
	{
		return "C3";
	}

	@Override
	public @NotNull PsiElementVisitor buildVisitor(
		@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session)
	{
		if (!(session.getFile() instanceof C3File)) return PsiElementVisitor.EMPTY_VISITOR;

		return new C3Visitor()
		{
			@Override
			public void visitStructDeclaration(@NotNull C3StructDeclaration struct)
			{
				if (hasEntryPointAttribute(struct.getAttributes())) return;
				String kind = isUnion(struct) ? "Union" : "Struct";
				report(struct.getTypeName(), kind);
			}

			@Override
			public void visitInterfaceDefinition(@NotNull C3InterfaceDefinition iface)
			{
				report(iface.getTypeName(), "Interface");
			}

			@Override
			public void visitFuncDef(@NotNull C3FuncDef funcDef)
			{
				// Only real function definitions with a body; skip interface method signatures and
				// extern declarations (those are contracts/implemented elsewhere, not unused code).
				C3FuncDefinition definition = funcDef.getParent() instanceof C3FuncDefinition d ? d : null;
				if (definition == null || definition.getMacroFuncBody() == null) return;

				String name = funcDef.getName();
				if (name == null) return;
				boolean isMethod = funcDef.getFuncHeader().getFuncName().getType() != null;
				if (!isMethod && name.equals("main")) return;
				if (hasEntryPointAttribute(funcDef.getAttributes())) return;
				if (isMethod && implementsInterfaceMethod(funcDef, name)) return;

				PsiElement nameElement = funcDef.getNameIdentElement();
				reportElement(nameElement != null ? nameElement : funcDef, funcDef, name,
					isMethod ? "Method" : "Function");
			}

			@Override
			public void visitParameter(@NotNull C3Parameter parameter)
			{
				C3FuncDefinition definition = PsiTreeUtil.getParentOfType(parameter, C3FuncDefinition.class);
				if (definition == null || definition.getMacroFuncBody() == null) return;

				String name = parameter.getNameIdent();
				if (name == null || name.equals("_")) return;

				// Parameters of contract/entry-point functions are expected to be unused.
				C3FuncDef funcDef = definition.getFuncDef();
				if (hasEntryPointAttribute(funcDef.getAttributes())) return;
				if (funcDef.getFuncHeader().getFuncName().getType() != null
					&& implementsInterfaceMethod(funcDef, funcDef.getName())) return;

				if (isReferenced(parameter, new LocalSearchScope(definition))) return;

				PsiElement nameElement = parameter.getNameIdentElement();
				flag(nameElement != null ? nameElement : parameter, name, "Parameter");
			}

			@Override
			public void visitEnumDeclaration(@NotNull C3EnumDeclaration enumDecl)
			{
				if (hasEntryPointAttribute(enumDecl.getAttributes())) return;
				report(enumDecl.getTypeName(), "Enum");
			}

			@Override
			public void visitTypedefDecl(@NotNull C3TypedefDecl typedef)
			{
				if (hasEntryPointAttribute(typedef.getAttributes())) return;
				report(typedef.getTypeName(), "Typedef");
			}

			@Override
			public void visitAliasTypeDecl(@NotNull C3AliasTypeDecl alias)
			{
				if (hasEntryPointAttribute(alias.getAttributes())) return;
				report(alias.getTypeName(), "Type alias");
			}

			@Override
			public void visitConstDeclarationStmt(@NotNull C3ConstDeclarationStmt constDecl)
			{
				if (hasEntryPointAttribute(constDecl.getAttributes())) return;
				String name = constDecl.getNameIdent();
				if (name == null) return;
				PsiElement nameElement = constDecl.getNameIdentElement();
				reportElement(nameElement != null ? nameElement : constDecl, constDecl, name, "Constant");
			}

			@Override
			public void visitFaultDefinition(@NotNull C3FaultDefinition fault)
			{
				if (hasEntryPointAttribute(fault.getAttributes())) return;
				String name = fault.getNameIdent();
				if (name == null) return;
				PsiElement nameElement = fault.getNameIdentElement();
				reportElement(nameElement != null ? nameElement : fault, fault, name, "Fault");
			}

			@Override
			public void visitGlobalDecl(@NotNull C3GlobalDecl global)
			{
				if (hasEntryPointAttribute(global.getAttributes())) return;
				// A global declaration may introduce several names: `int a, b, c;`.
				for (PsiElement child = global.getFirstChild(); child != null; child = child.getNextSibling())
				{
					if (child.getNode().getElementType() != C3Types.IDENT) continue;
					String name = child.getText();
					if (!hasCodeUsageElsewhere(global, child, name, projectScope(global)))
					{
						flag(child, name, "Global");
					}
				}
			}

			@Override
			public void visitAliasDecl(@NotNull C3AliasDecl alias)
			{
				if (hasEntryPointAttribute(alias.getAttributes())) return;
				C3AliasName aliasName = alias.getAliasName();
				if (aliasName == null) return;
				String name = aliasName.getText();
				// '@'-prefixed alias names tokenize differently; skip rather than risk a false positive.
				if (name.isEmpty() || name.startsWith("@")) return;
				if (!hasCodeUsageElsewhere(alias, aliasName, name, projectScope(alias)))
				{
					flag(aliasName, name, "Alias");
				}
			}

			@Override
			public void visitLocalDeclAfterType(@NotNull C3LocalDeclAfterType local)
			{
				// Only plain statement declarations (`int a;`, `Foo x = ...;`, `int a, b;`); skip
				// declarations embedded in for-loop / if headers, and compile-time ($x) variables.
				if (PsiTreeUtil.getParentOfType(local, C3LocalDeclarationStmt.class) == null) return;
				String name = local.getNameIdent();
				if (name == null || name.equals("_") || name.startsWith("$")) return;

				PsiElement scopeElement = PsiTreeUtil.getParentOfType(local, C3FuncDefinition.class);
				SearchScope scope = scopeElement != null
					? new LocalSearchScope(scopeElement)
					: GlobalSearchScope.fileScope(local.getContainingFile());

				PsiElement nameElement = local.getNameIdentElement();
				if (hasCodeUsageElsewhere(local, nameElement != null ? nameElement : local, name, scope)) return;
				flag(nameElement != null ? nameElement : local, name, "Variable");
			}

			/** Reports {@code typeName} (a type declaration name) if it has no references. */
			private void report(@Nullable C3TypeName typeName, @NotNull String kind)
			{
				if (typeName == null) return;
				String name = typeName.getName();
				if (name == null) return;
				PsiElement nameElement = typeName.getNameIdentElement();
				reportElement(nameElement != null ? nameElement : typeName, typeName, name, kind);
			}

			/**
			 * @param reportElement the (name) element to grey out
			 * @param searchElement the identity element references resolve to
			 */
			private void reportElement(
				@NotNull PsiElement reportElement, @NotNull PsiElement searchElement,
				@NotNull String name, @NotNull String kind)
			{
				if (isReferenced(searchElement, null)) return;
				flag(reportElement, name, kind);
			}

			private void flag(@NotNull PsiElement reportElement, @NotNull String name, @NotNull String kind)
			{
				holder.registerProblem(
					reportElement,
					kind + " '" + name + "' is never used",
					ProblemHighlightType.LIKE_UNUSED_SYMBOL,
					new DeleteUnusedDeclarationFix(kind.toLowerCase(Locale.ROOT) + " '" + name + "'"));
			}
		};
	}

	private static boolean isReferenced(@NotNull PsiElement element, @Nullable LocalSearchScope scope)
	{
		PsiReference reference = scope != null
			? ReferencesSearch.search(element, scope).findFirst()
			: ReferencesSearch.search(element).findFirst();
		return reference != null;
	}

	/**
	 * Fallback for declarations the resolve infrastructure does not cover precisely (globals, plain
	 * aliases, and local variables — whose resolver is scoped to a single block): is {@code name}
	 * mentioned in code within {@code scope} anywhere other than at the declaration itself? This
	 * over-counts (an unrelated identifier with the same text reads as a use), so it can only
	 * under-report — it never greys out a declaration that is actually referenced.
	 */
	private static boolean hasCodeUsageElsewhere(
		@NotNull PsiElement declaration, @NotNull PsiElement nameElement,
		@NotNull String name, @NotNull SearchScope scope)
	{
		Project project = declaration.getProject();
		PsiFile declarationFile = declaration.getContainingFile();
		int nameOffset = nameElement.getTextRange().getStartOffset();
		boolean[] usedElsewhere = { false };

		PsiSearchHelper.getInstance(project).processElementsWithWord(
			(element, offsetInElement) -> {
				boolean isDeclarationItself = element.getContainingFile() == declarationFile
					&& element.getTextRange().getStartOffset() + offsetInElement == nameOffset;
				if (isDeclarationItself) return true;
				usedElsewhere[0] = true;
				return false;
			},
			scope,
			name,
			UsageSearchContext.IN_CODE,
			true);

		return usedElsewhere[0];
	}

	private static @NotNull SearchScope projectScope(@NotNull PsiElement element)
	{
		return GlobalSearchScope.projectScope(element.getProject());
	}

	private static boolean isUnion(@NotNull C3StructDeclaration struct)
	{
		PsiElement keyword = struct.getFirstChild();
		return keyword != null && "union".equals(keyword.getText());
	}

	private static boolean hasEntryPointAttribute(@Nullable C3Attributes attributes)
	{
		if (attributes == null) return false;
		for (C3Attribute attribute : attributes.getAttributeList())
		{
			String text = attribute.getAttributeName().getText();
			String simple = text.substring(text.lastIndexOf(':') + 1).replace("@", "").toLowerCase(Locale.ROOT);
			if (ENTRY_POINT_ATTRIBUTES.contains(simple)) return true;
		}
		return false;
	}

	/** True if {@code funcDef} (a method on its receiver type) satisfies an implemented interface. */
	private static boolean implementsInterfaceMethod(@NotNull C3FuncDef funcDef, @Nullable String methodName)
	{
		if (methodName == null) return false;
		C3Type receiver = funcDef.getFuncHeader().getFuncName().getType();
		if (receiver == null) return false;
		String text = receiver.getText();
		String structName = text.substring(Math.max(text.lastIndexOf('.'), text.lastIndexOf(':')) + 1).trim();
		if (structName.isEmpty()) return false;

		Project project = funcDef.getProject();
		for (C3FullyQualifiedNamePsiElement element :
			NameIndexService.INSTANCE.findByNameEndsWith(structName, project))
		{
			if (!(element instanceof C3TypeName typeName)
				|| !structName.equals(typeName.getName())
				|| !(typeName.getParent() instanceof C3StructDeclaration struct))
			{
				continue;
			}
			for (C3InterfaceDefinition iface : C3Interfaces.implementedInterfaces(struct))
			{
				for (C3FuncDef method : iface.getInterfaceBody().getFuncDefList())
				{
					if (methodName.equals(C3Interfaces.methodSimpleName(method))) return true;
				}
			}
		}
		return false;
	}
}
