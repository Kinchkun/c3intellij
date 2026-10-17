package org.c3lang.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import org.c3lang.intellij.psi.*;
import org.c3lang.intellij.stubs.C3FuncDefStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * Hand-written behaviour for a C3 function definition ({@code func_def} rule).
 *
 * <p>This is the canonical worked example of the mixin pattern (see {@link org.c3lang.intellij.psi.C3FuncDefMixin}).
 * The generated {@code C3FuncDefImpl} extends this class, so the methods here become part of every
 * function-definition PSI element.
 *
 * <p><strong>Stub-first pattern:</strong> notice how almost every getter first asks
 * {@code getGreenStub()}. When the IDE only has the indexed stub (the file has not been parsed), the
 * answer comes straight from the cheap stub data. Only when there is no stub do we fall back to
 * walking the parsed AST ({@code getFuncHeader()...}). This is what makes cross-file features fast:
 * resolving or completing a call elsewhere never forces this file to be fully parsed.
 *
 * <p>The constructors mirror the three modes of {@link C3StubBasedPsiElementBase} (AST-only,
 * stub-only, and both).
 */
public abstract class C3FuncDefMixinImpl extends C3StubBasedPsiElementBase<C3FuncDefStub> implements C3FuncDef
{
	public C3FuncDefMixinImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	public C3FuncDefMixinImpl(@NotNull C3FuncDefStub stub, @NotNull IStubElementType<?, ?> nodeType)
	{
		super(stub, nodeType);
	}

	public C3FuncDefMixinImpl(@NotNull C3FuncDefStub stub, @Nullable IElementType nodeType, @Nullable ASTNode node)
	{
		super(stub, nodeType, node);
	}

	@Override
	public @Nullable String getName()
	{
		return getNameIdent();
	}

	@Override
	public @NotNull PsiElement setName(@NotNull String name)
	{
		// Rename: physically replace the identifier token's text. Invoked via NamedElementManipulator.
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
		// The function's name token is the last child of its func_name node (after any receiver type).
		PsiElement last = getFuncHeader().getFuncName().getLastChild();
		return last instanceof LeafPsiElement ? (LeafPsiElement) last : null;
	}

	@Override
	public int getTextOffset()
	{
		LeafPsiElement ident = getNameIdentElement();
		return ident != null ? ident.getTextOffset() : super.getTextOffset();
	}

	@Override
	public @NotNull String getSourceFileName()
	{
		C3FuncDefStub s = getGreenStub();
		return s != null ? s.getSourceFileName() : getContainingFile().getName();
	}

	@Override
	public @NotNull FullyQualifiedName getFqName()
	{
		// Stub-first: read the precomputed FQN from the index when available, else derive it from the AST.
		C3FuncDefStub s = getGreenStub();
		return s != null ? s.getFqName() : FullyQualifiedName.Companion.from(getFuncHeader(), getModuleName());
	}

	@Override
	public @Nullable ModuleName getModuleName()
	{
		C3FuncDefStub s = getGreenStub();
		return s != null ? s.getModule() : ModuleName.Companion.from(this);
	}

	@Override
	public @Nullable ShortType getType()
	{
		C3FuncDefStub s = getGreenStub();
		if (s != null) return s.getType();
		C3Type t = getFuncHeader().getFuncName().getType();
		return t != null ? ShortType.Companion.toShortType(t) : null;
	}

	@Override
	public @Nullable ShortType getReturnType()
	{
		C3FuncDefStub s = getGreenStub();
		if (s != null) return s.getReturnType();
		C3Type t = getFuncHeader().getOptionalType().getType();
		return ShortType.Companion.toShortType(t);
	}

	@Override
	public @NotNull List<ParamType> getParameterTypes()
	{
		C3FuncDefStub s = getGreenStub();
		if (s != null) return s.getParameterTypes();
		C3ParameterList paramList = getFnParameterList().getParameterList();
		List<C3ParamDecl> paramDecls = paramList != null ? paramList.getParamDeclList() : null;
		return ParamType.Companion.toParamTypeList(paramDecls);
	}
}
