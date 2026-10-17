package org.c3lang.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.completion.CompletionExtensionsKt;
import org.c3lang.intellij.index.NameIndexService;
import org.c3lang.intellij.index.StructService;
import org.c3lang.intellij.psi.*;
import org.c3lang.intellij.psi.reference.C3ReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The central resolver for "path identifiers" – plain names like {@code myVar}, {@code myFunc} or
 * {@code module::thing} as they appear in expressions.
 *
 * <p>This mixin is where Ctrl-click on most identifiers begins. The key method is
 * {@link #getReference()}: based on <em>where</em> the identifier sits, it picks one of several small
 * {@link C3ReferenceBase} strategies, each of which knows how to find that kind of declaration:
 * <ul>
 *   <li>{@link C3LocalDeclAfterTypeReference} – a local variable declared earlier in the same block.</li>
 *   <li>{@link C3ParameterReference} – a parameter of the enclosing function/macro.</li>
 *   <li>{@link C3FuncNameReference} – a function/macro call; looked up cross-file via the name index.</li>
 *   <li>{@link C3StructMemberReference} – a struct field/member access.</li>
 * </ul>
 * The first three are local (cheap tree walks); only {@code C3FuncNameReference} reaches into the
 * project-wide index ({@link NameIndexService}) and filters by module visibility – this is the step
 * that resolves names declared in other files.
 */
public abstract class C3PathIdentMixinImpl extends C3PsiNamedElementImpl implements C3PathIdent
{
	public C3PathIdentMixinImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	@Override
	public @Nullable String getName()
	{
		return getNameIdent();
	}

	@Override
	public @Nullable PsiElement setName(@NotNull String name)
	{
		LeafPsiElement ident = getNameIdentElement();
		if (ident != null) ident.replaceWithText(name);
		return this;
	}

	@Override
	public @Nullable PsiElement getNameIdentifier()
	{
		return getNameIdentElement();
	}

	@Override
	public int getTextOffset()
	{
		LeafPsiElement ident = getNameIdentElement();
		return ident != null ? ident.getTextOffset() : super.getTextOffset();
	}

	@Override
	public @Nullable String getNameIdent()
	{
		LeafPsiElement ident = getNameIdentElement();
		return ident != null ? ident.getText() : null;
	}

	@Override
	public @Nullable LeafPsiElement getNameIdentElement()
	{
		PsiElement last = getLastChild();
		return last instanceof LeafPsiElement ? (LeafPsiElement) last : null;
	}

	@Override
	public @Nullable FullyQualifiedName findTypeName()
	{
		List<C3LocalDeclAfterType> decls = findLocalDeclAfterType();
		if (decls.size() == 1) return decls.getFirst().findTypeName();

		// The receiver may be a function/macro parameter rather than a local declaration
		// (e.g. `Formatter formatter` → Formatter), so member completion works on parameters too.
		Collection<C3PsiElement> params = new C3ParameterReference(this).multiResolve();
		if (params.size() == 1 && params.iterator().next() instanceof C3Parameter parameter)
		{
			return parameter.findTypeName();
		}
		return null;
	}

	@Override
	public @NotNull List<C3LocalDeclAfterType> findLocalDeclAfterType()
	{
		C3CompoundStatement compoundStatement =
			PsiTreeUtil.getParentOfType(this, C3CompoundStatement.class);
		if (compoundStatement == null) return Collections.emptyList();

		Collection<C3LocalDeclAfterType> all =
			PsiTreeUtil.collectElementsOfType(compoundStatement, C3LocalDeclAfterType.class);
		C3LocalDeclAfterType best = null;
		for (C3LocalDeclAfterType decl : all)
		{
			if (decl.getTextOffset() < getTextOffset()
				&& decl.getNameIdent() != null
				&& decl.getNameIdent().equals(getNameIdent())
				&& (best == null || decl.getTextOffset() > best.getTextOffset()))
			{
				best = decl;
			}
		}
		return best != null ? Collections.singletonList(best) : Collections.emptyList();
	}


	private boolean hasLocalDeclBeforeUse()
	{
		return !new C3LocalDeclAfterTypeReference(this).multiResolve().isEmpty();
	}

	private boolean hasParameterBeforeUse()
	{
		return !new C3ParameterReference(this).multiResolve().isEmpty();
	}

	private boolean isStructMemberAccess()
	{
		return CompletionExtensionsKt.getRootType(this) != null
			&& PsiTreeUtil.getParentOfType(this, C3PathNameProvider.class) != null;
	}

	private boolean isCallablePosition()
	{
		return isCallCallee() || isReflectOperand() || isAddressOfOperand();
	}

	private boolean isCallCallee()
	{
		C3PathIdentExpr expr = PsiTreeUtil.getParentOfType(this, C3PathIdentExpr.class);
		if (expr == null) return false;

		C3CallExpr call = PsiTreeUtil.getParentOfType(expr, C3CallExpr.class);
		return call != null && call.getExpr() == expr;
	}

	private boolean isReflectOperand()
	{
		C3PathIdentExpr expr = PsiTreeUtil.getParentOfType(this, C3PathIdentExpr.class);
		if (expr == null) return false;

		C3CtAnalyzeExpr analyzeExpr = PsiTreeUtil.getParentOfType(expr, C3CtAnalyzeExpr.class);
		if (analyzeExpr == null) return false;

		return analyzeExpr.getCtAnalyze().getText().equals("$reflect")
			&& analyzeExpr.getGroupedExpr() != null
			&& PsiTreeUtil.isAncestor(analyzeExpr.getGroupedExpr(), expr, false);
	}

	private boolean isAddressOfOperand()
	{
		C3PathIdentExpr expr = PsiTreeUtil.getParentOfType(this, C3PathIdentExpr.class);
		if (expr == null) return false;

		C3UnaryExpr unaryExpr = PsiTreeUtil.getParentOfType(expr, C3UnaryExpr.class);
		if (unaryExpr == null) return false;

		return unaryExpr.getExpr() == expr && unaryExpr.getUnaryOp().getText().equals("&");
	}

	/**
	 * Chooses how this identifier should resolve, based on its surrounding context. Order matters:
	 * locals and parameters shadow same-named globals, so they are tried first.
	 */
	@Override
	public @NotNull PsiReference getReference()
	{
		if (hasLocalDeclBeforeUse()) return new C3LocalDeclAfterTypeReference(this);
		if (hasParameterBeforeUse()) return new C3ParameterReference(this);
		if (isCallablePosition()) return new C3FuncNameReference(this);
		if (isStructMemberAccess()) return new C3StructMemberReference(this);
		return new C3LocalDeclAfterTypeReference(this); // default: try a local declaration
	}

	/** Resolves to the nearest preceding local declaration of the same name in the enclosing block. */
	private static class C3LocalDeclAfterTypeReference extends C3ReferenceBase<C3PathIdent>
	{
		C3LocalDeclAfterTypeReference(@NotNull C3PathIdent element)
		{
			super(element);
		}

		@Override
		public @NotNull Collection<C3PsiElement> multiResolve()
		{
			C3CompoundStatement compoundStatement =
				PsiTreeUtil.getParentOfType(myElement, C3CompoundStatement.class);
			if (compoundStatement == null) return Collections.emptyList();

			Collection<C3LocalDeclAfterType> all =
				PsiTreeUtil.collectElementsOfType(compoundStatement, C3LocalDeclAfterType.class);
			C3LocalDeclAfterType best = null;
			for (C3LocalDeclAfterType decl : all)
			{
				if (decl.getTextOffset() < myElement.getTextOffset()
					&& decl.getNameIdent() != null
					&& decl.getNameIdent().equals(myElement.getNameIdent())
					&& (best == null || decl.getTextOffset() > best.getTextOffset()))
				{
					best = decl;
				}
			}
			return best != null ? Collections.singletonList(best) : Collections.emptyList();
		}
	}

	/** Resolves to a parameter of the enclosing function/macro with a matching name. */
	private static class C3ParameterReference extends C3ReferenceBase<C3PathIdent>
	{
		C3ParameterReference(@NotNull C3PathIdent element)
		{
			super(element);
		}

		@Override
		public @NotNull Collection<C3PsiElement> multiResolve()
		{
			C3FuncDefinition funcDef =
				PsiTreeUtil.getParentOfType(myElement, C3FuncDefinition.class);
			if (funcDef == null) return Collections.emptyList();

			Collection<C3Parameter> params =
				PsiTreeUtil.collectElementsOfType(funcDef, C3Parameter.class);
			for (C3Parameter param : params)
			{
				if (param.getNameIdent() != null && param.getNameIdent().equals(myElement.getNameIdent()))
				{
					return Collections.singleton(param);
				}
			}
			return Collections.emptyList();
		}
	}

	/**
	 * Resolves a function/macro call. This is the cross-file step: it queries the project-wide
	 * {@link NameIndexService} for declarations whose name matches, then keeps only those that are
	 * actually visible from here (same module, or an imported one).
	 */
	private static class C3FuncNameReference extends C3ReferenceBase<C3PathIdent>
	{
		C3FuncNameReference(@NotNull C3PathIdent element)
		{
			super(element);
		}

		@Override
		public @NotNull Collection<C3PsiElement> multiResolve()
		{
			C3ModuleDefinition moduleDefinition = myElement.getModuleDefinition();
			List<C3PsiElement> result = new ArrayList<>();
			// Ask the index for all declarations whose FQN ends with the typed name (cheap, no parsing).
			for (C3FullyQualifiedNamePsiElement el :
				NameIndexService.INSTANCE.findByNameEndsWith(myElement.getText(), myElement.getProject()))
			{
				if (el instanceof C3CallablePsiElement
					&& el.getFqName().getName().equals(myElement.getNameIdent())
					&& moduleDefinition.containsImportOrSameModule(el)) // visibility filter
				{
					result.add(el);
				}
			}
			return result;
		}

		@Override
		public @NotNull TextRange getRangeInElement()
		{
			C3Path path = myElement.getPath();
			return TextRange.create(path != null ? path.getTextLength() : 0, myElement.getTextLength());
		}
	}

	/**
	 * Resolves a struct member access (e.g. the {@code host} in {@code client.socket.host}). It finds
	 * the type of the receiver, then walks the chain of field names through {@link StructService} to
	 * land on the member declaration.
	 */
	private static class C3StructMemberReference extends C3ReferenceBase<C3PathIdent>
	{
		C3StructMemberReference(@NotNull C3PathIdent element)
		{
			super(element);
		}

		@Override
		public @NotNull Collection<C3PsiElement> multiResolve()
		{
			FullyQualifiedName rootType = CompletionExtensionsKt.getRootType(myElement);
			if (rootType == null) return Collections.emptyList();

			C3Arg parentArg = PsiTreeUtil.getParentOfType(myElement, C3Arg.class);
			C3PathNameProvider pathNameProvider =
				PsiTreeUtil.getParentOfType(myElement, C3PathNameProvider.class);
			if (pathNameProvider == null) return Collections.emptyList();
			List<String> path = pathNameProvider.findPathName(false);

			// Walk up through all C3PathNameProvider ancestors of parentArg
			List<String> fieldNames = new ArrayList<>();
			C3PathNameProvider currentProvider = parentArg != null
				? PsiTreeUtil.getParentOfType(parentArg, C3PathNameProvider.class)
				: null;
			while (currentProvider != null)
			{
				fieldNames.addAll(currentProvider.findPathName(false));
				currentProvider = PsiTreeUtil.getParentOfType(
					(PsiElement) currentProvider, C3PathNameProvider.class);
			}
			Collections.reverse(fieldNames);

			List<String> paths = new ArrayList<>(fieldNames);
			paths.addAll(path);
			paths.add(myElement.getText());

			return new ArrayList<>(
				StructService.INSTANCE.getStructMemberDeclaration(rootType, paths, myElement.getProject()));
		}
	}
}
