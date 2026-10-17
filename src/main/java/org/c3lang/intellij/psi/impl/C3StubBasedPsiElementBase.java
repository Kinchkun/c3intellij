package org.c3lang.intellij.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.c3lang.intellij.psi.C3PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base implementation for C3 elements that can be backed by a <em>stub</em>.
 *
 * <p>Top-level declarations (functions, types, constants, modules...) need to be discoverable across
 * the whole project from the index, so they are stub-backed. {@link StubBasedPsiElementBase} can live
 * in one of two modes: backed by an {@link ASTNode} (when the file is parsed) <em>or</em> backed only
 * by a deserialized {@code StubT} (when the file has not been parsed and we only have the index). The
 * three constructors mirror those cases; the platform picks the right one.
 *
 * <p>The hand-written {@code C3*MixinImpl} classes for stub-backed rules extend this and read their
 * data from the stub when present (fast) or from the AST otherwise. See {@code stubs/C3FuncDefStub}
 * and {@code psi/impl/C3FuncDefMixinImpl} for a worked example.
 *
 * @param <StubT> the stub type holding this element's indexed data
 */
public class C3StubBasedPsiElementBase<StubT extends StubElement<?>> extends StubBasedPsiElementBase<StubT>
	implements C3PsiElement
{
	public C3StubBasedPsiElementBase(@NotNull StubT stub, @NotNull IStubElementType<?, ?> nodeType)
	{
		super(stub, nodeType);
	}

	public C3StubBasedPsiElementBase(@NotNull ASTNode node)
	{
		super(node);
	}

	public C3StubBasedPsiElementBase(@NotNull StubT stub, @Nullable IElementType nodeType, @Nullable ASTNode node)
	{
		super(stub, nodeType, node);
	}

	@Override
	public @Nullable StubT getStub()
	{
		// getGreenStub() returns the stub even after the AST has been loaded, so reads stay cheap
		// and consistent whether or not the file has been parsed.
		return getGreenStub();
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName() + "(" + getNode().getElementType() + ")";
	}
}
