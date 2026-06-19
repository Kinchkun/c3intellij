package org.c3lang.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
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

public abstract class C3AccessIdentMixinImpl extends C3PsiNamedElementImpl implements C3AccessIdent
{
	public C3AccessIdentMixinImpl(@NotNull ASTNode node)
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
	public @Nullable String getNameIdent()
	{
		LeafPsiElement ident = getNameIdentElement();
		return ident != null ? ident.getText() : null;
	}

	@Override
	public @Nullable LeafPsiElement getNameIdentElement()
	{
		PsiElement first = getFirstChild();
		return first instanceof LeafPsiElement ? (LeafPsiElement) first : null;
	}

	@Override
	public int getTextOffset()
	{
		LeafPsiElement ident = getNameIdentElement();
		return ident != null ? ident.getTextOffset() : super.getTextOffset();
	}

	@Override
	public @NotNull TextRange getTextRange()
	{
		LeafPsiElement ident = getNameIdentElement();
		return ident != null ? ident.getTextRange() : super.getTextRange();
	}

	@Override
	public @NotNull PsiReference getReference()
	{
		return new StructMemberReference(this);
	}

	@Override
	public @Nullable FullyQualifiedName findTypeName()
	{
		C3PsiElement resolved = new StructMemberReference(this).resolve();
		if (!(resolved instanceof C3FullyQualifiedTypeNameProvider)) return null;
		return ((C3FullyQualifiedTypeNameProvider) resolved).findTypeName();
	}

	private static class StructMemberReference extends C3ReferenceBase<C3AccessIdent>
	{
		StructMemberReference(@NotNull C3AccessIdent element)
		{
			super(element);
		}

		@Override
		public @NotNull Collection<C3PsiElement> multiResolve()
		{
			C3CallExpr call = PsiTreeUtil.getParentOfType(myElement, C3CallExpr.class);
			if (call == null) return Collections.emptyList();

			AccessIdentSequence seq = getAccessIdentSequence(call);
			if (seq == null) return Collections.emptyList();

			String query = seq.rootType.getFullName();
			List<C3StructMemberDeclaration> structMembers = Collections.emptyList();

			for (int i = 0; i < seq.idents.size(); i++)
			{
				String ident = seq.idents.get(i);
				structMembers = StructService.INSTANCE.getStructMembers(query + "." + ident, myElement.getProject());
				C3StructMemberDeclaration member = structMembers.size() == 1 ? structMembers.get(0) : null;
				FullyQualifiedName nextType = member != null ? member.getStructPathType() : null;
				if (nextType == null)
				{
					// Not a data field. A method (type-bound function) call such as
					// `client.init(...)` terminates the access chain, so resolve the final
					// ident against the methods declared on the current type.
					if (i == seq.idents.size() - 1)
					{
						List<C3PsiElement> methods = resolveMethods(query, ident, myElement);
						if (!methods.isEmpty()) return methods;
					}
					return Collections.emptyList();
				}
				query = nextType.getFullName();
			}

			return new ArrayList<>(structMembers);
		}

		/**
		 * Resolves {@code <type>.<ident>} to the matching method definitions
		 * (type-bound {@code fn}/{@code macro}) that are visible from {@code context}.
		 */
		private static @NotNull List<C3PsiElement> resolveMethods(
			@NotNull String typeFullName, @NotNull String ident, @NotNull C3AccessIdent context)
		{
			FullyQualifiedName type = FullyQualifiedName.parse(typeFullName);
			String methodName = type.getName() + "." + ident;
			C3ModuleDefinition moduleDefinition = context.getModuleDefinition();

			List<C3PsiElement> result = new ArrayList<>();
			for (C3FullyQualifiedNamePsiElement el :
				NameIndexService.INSTANCE.findByNameEndsWith("." + ident, context.getProject()))
			{
				if (el instanceof C3CallablePsiElement callable
					&& callable.getType() != null
					&& el.getFqName().getName().equals(methodName)
					&& moduleDefinition.containsImportOrSameModule(el))
				{
					result.add(el);
				}
			}
			return result;
		}

		@Override
		public @NotNull TextRange getRangeInElement()
		{
			return super.getRangeInElement();
		}

		private static @Nullable AccessIdentSequence getAccessIdentSequence(@NotNull C3CallExpr callExpr)
		{
			List<C3PsiElement> accessSequence = new ArrayList<>();
			C3PsiElement current = callExpr;
			while (current != null)
			{
				accessSequence.add(current);
				if (current instanceof C3ExprStmt)
				{
					current = (C3PsiElement) ((C3ExprStmt) current).getExpr();
				}
				else if (current instanceof C3CallExpr)
				{
					current = (C3PsiElement) ((C3CallExpr) current).getExpr();
				}
				else
				{
					break;
				}
			}

			if (accessSequence.isEmpty()) return null;

			C3PsiElement last = accessSequence.remove(accessSequence.size() - 1);
			if (!(last instanceof C3PathIdentExpr)) return null;

			C3PathIdentExpr rootExpr = (C3PathIdentExpr) last;
			FullyQualifiedName rootType = rootExpr.getPathIdent().findTypeName();
			if (rootType == null) return null;

			List<String> idents = new ArrayList<>();
			for (C3PsiElement elem : accessSequence)
			{
				if (!(elem instanceof C3CallExpr)) continue;

				// Take the member name from this call level's `.name` access-ident PSI rather than by
				// splitting the call text: an invocation tail `(args)` contributes no member, and
				// arguments may contain their own dots (e.g. `Enum.CONST`), which corrupted the split.
				C3CallExprTail tail = PsiTreeUtil.getChildOfType(elem, C3CallExprTail.class);
				C3AccessIdent access = tail != null ? PsiTreeUtil.getChildOfType(tail, C3AccessIdent.class) : null;
				if (access != null && access.getNameIdent() != null) idents.add(access.getNameIdent());
			}
			Collections.reverse(idents);

			return new AccessIdentSequence(rootType, idents);
		}
	}

	private static final class AccessIdentSequence
	{
		final FullyQualifiedName rootType;
		final List<String> idents;

		AccessIdentSequence(@NotNull FullyQualifiedName rootType, @NotNull List<String> idents)
		{
			this.rootType = rootType;
			this.idents = idents;
		}
	}
}
