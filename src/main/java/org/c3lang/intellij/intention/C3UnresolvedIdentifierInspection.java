package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.EditDistance;
import org.c3lang.intellij.index.NameIndex;
import org.c3lang.intellij.psi.C3Attribute;
import org.c3lang.intellij.psi.C3CatchUnwrap;
import org.c3lang.intellij.psi.C3CtDefinedExpr;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3ForeachVar;
import org.c3lang.intellij.psi.C3FuncDefinition;
import org.c3lang.intellij.psi.C3LambdaDeclExpr;
import org.c3lang.intellij.psi.C3LambdaDeclShortExpr;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3MacroDefinition;
import org.c3lang.intellij.psi.C3NameIdentProvider;
import org.c3lang.intellij.psi.C3ParamPath;
import org.c3lang.intellij.psi.C3Parameter;
import org.c3lang.intellij.psi.C3PathIdent;
import org.c3lang.intellij.psi.C3PathIdentExpr;
import org.c3lang.intellij.psi.C3PsiElement;
import org.c3lang.intellij.psi.C3TryUnwrap;
import org.c3lang.intellij.psi.C3Types;
import org.c3lang.intellij.psi.C3VarDecl;
import org.c3lang.intellij.psi.C3Visitor;
import org.c3lang.intellij.psi.ModuleName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports a plain name used as a value that names nothing: {@code return respons;} where only
 * {@code response} is declared. The compiler says "'respons' could not be found, did you spell it
 * right?".
 *
 * <p>Only an unqualified lower-case identifier inside a function, macro or lambda body is checked.
 * Such a name is a local, a parameter, a declaration of the same module, or a {@code @builtin} of
 * the standard library; a declaration of another module must be written with its path
 * ({@code io::printn}). So when the name resolves to none of them it is wrong, provided the
 * standard library is indexed; without it the builtins ({@code mem}, {@code abort}) would all be
 * reported, and the inspection stays quiet.</p>
 *
 * <p>A name that is declared anywhere in the project or its libraries stays quiet as well: the
 * resolver does not model every legal use (a macro passed by name), and a false error is worse
 * than a missed one.</p>
 *
 * <p>The quick fix <i>Change to 'response'</i> is offered for each local, parameter or declaration
 * of the same module whose name is at most two edits away.</p>
 */
public final class C3UnresolvedIdentifierInspection extends LocalInspectionTool
{
	/** A builtin every standard library declares; its presence tells that the library is indexed. */
	private static final String STDLIB_PROBE = "std::core::builtin::unreachable";

	/** Functions the compiler provides without a declaration in the standard library. */
	private static final Set<String> COMPILER_BUILTINS = Set.of("lengthof");

	private static final int MAX_EDIT_DISTANCE = 2;
	private static final int MAX_SUGGESTIONS = 3;

	@Override
	public @NotNull String getDisplayName()
	{
		return "Unresolved identifier";
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
		if (!isStandardLibraryIndexed(holder.getProject())) return PsiElementVisitor.EMPTY_VISITOR;

		return new C3Visitor()
		{
			@Override
			public void visitPathIdent(@NotNull C3PathIdent ident)
			{
				if (!(ident.getParent() instanceof C3PathIdentExpr) || ident.getPath() != null) return;
				LeafPsiElement nameElement = ident.getNameIdentElement();
				if (nameElement == null || nameElement.getElementType() != C3Types.IDENT) return;
				PsiElement callable = enclosingCallable(ident);
				if (callable == null) return;
				// `$defined(name)` asks whether the name exists.
				if (PsiTreeUtil.getParentOfType(ident, C3CtDefinedExpr.class) != null) return;
				// A field designator (`{ .size = 1 }`) and an attribute argument (`@operator(len)`)
				// are names of other kinds.
				if (PsiTreeUtil.getParentOfType(ident, C3ParamPath.class, C3Attribute.class) != null) return;
				if (resolves(ident)) return;
				if (COMPILER_BUILTINS.contains(nameElement.getText()) || isDeclaredSomewhere(nameElement.getText(), holder.getProject())) return;

				String name = nameElement.getText();
				List<LocalQuickFix> fixes = new ArrayList<>();
				for (String candidate : similarNames(ident, callable, name)) fixes.add(new RenameUsageFix(candidate));
				holder.registerProblem(
					nameElement,
					"'" + name + "' could not be found, did you spell it right?",
					ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
					fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
			}
		};
	}

	private static boolean isStandardLibraryIndexed(@NotNull Project project)
	{
		return !StubIndex.getElements(
			NameIndex.KEY, STDLIB_PROBE, project, GlobalSearchScope.allScope(project), C3PsiElement.class).isEmpty();
	}

	/**
	 * Whether any indexed declaration has this simple name, in whatever module. Resolution misses
	 * some legal uses (a macro passed by name, a declaration of a module section the usage does not
	 * see through the index), so a name that exists somewhere stays quiet.
	 */
	private static boolean isDeclaredSomewhere(@NotNull String name, @NotNull Project project)
	{
		for (String key : StubIndex.getInstance().getAllKeys(NameIndex.KEY, project))
		{
			if (key.equals(name) || key.endsWith("::" + name)) return true;
		}
		return false;
	}

	private static boolean resolves(@NotNull C3PathIdent ident)
	{
		PsiReference reference = ident.getReference();
		if (reference instanceof PsiPolyVariantReference polyVariant) return polyVariant.multiResolve(false).length > 0;
		return reference.resolve() != null;
	}

	private static @Nullable PsiElement enclosingCallable(@NotNull PsiElement element)
	{
		return PsiTreeUtil.getParentOfType(element,
			C3FuncDefinition.class, C3MacroDefinition.class, C3LambdaDeclExpr.class, C3LambdaDeclShortExpr.class);
	}

	/**
	 * Names spelled like {@code name}, closest first: the locals declared before the usage, the
	 * parameters, and the declarations of the usage's module.
	 */
	private static @NotNull List<String> similarNames(@NotNull C3PathIdent usage, @NotNull PsiElement callable, @NotNull String name)
	{
		Set<String> declared = new LinkedHashSet<>();
		for (PsiElement element : PsiTreeUtil.collectElements(callable, C3UnresolvedIdentifierInspection::isLocalDeclaration))
		{
			if (element.getTextOffset() >= usage.getTextOffset()) continue;
			String declaredName = element instanceof C3Parameter parameter
				? parameter.getName()
				: ((C3NameIdentProvider) element).getNameIdent();
			if (declaredName != null) declared.add(declaredName);
		}

		// Functions, macros and globals of the usage's module (`swapFloat` for `swap_float`); methods
		// (`Type.name`) are not called by a bare name.
		ModuleName module = usage.getModuleDefinition().getModuleName();
		if (module != null)
		{
			String prefix = module.getValue() + "::";
			for (String key : StubIndex.getInstance().getAllKeys(NameIndex.KEY, usage.getProject()))
			{
				if (!key.startsWith(prefix)) continue;
				String declaredName = key.substring(prefix.length());
				if (!declaredName.isEmpty() && Character.isLowerCase(declaredName.charAt(0)) && !declaredName.contains(".") && !declaredName.contains(":"))
				{
					declared.add(declaredName);
				}
			}
		}

		List<String> similar = new ArrayList<>();
		for (String candidate : declared)
		{
			if (candidate.equals(name) || candidate.length() < 2) continue;
			if (EditDistance.optimalAlignment(candidate, name, true) <= MAX_EDIT_DISTANCE) similar.add(candidate);
		}
		similar.sort(Comparator.comparingInt(candidate -> EditDistance.optimalAlignment(candidate, name, true)));
		return similar.size() > MAX_SUGGESTIONS ? similar.subList(0, MAX_SUGGESTIONS) : similar;
	}

	private static boolean isLocalDeclaration(@NotNull PsiElement element)
	{
		return element instanceof C3LocalDeclAfterType || element instanceof C3ForeachVar
			|| element instanceof C3CatchUnwrap || element instanceof C3TryUnwrap || element instanceof C3VarDecl
			|| element instanceof C3Parameter;
	}

	/** Replaces the unresolved name with a similarly spelled declared one. */
	private static final class RenameUsageFix implements LocalQuickFix
	{
		private final String replacement;

		private RenameUsageFix(@NotNull String replacement)
		{
			this.replacement = replacement;
		}

		@Override
		public @NotNull String getName()
		{
			return "Change to '" + replacement + "'";
		}

		@Override
		public @NotNull String getFamilyName()
		{
			return "Change to a similar name";
		}

		@Override
		public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
		{
			if (descriptor.getPsiElement() instanceof LeafPsiElement leaf) leaf.replaceWithText(replacement);
		}
	}
}
