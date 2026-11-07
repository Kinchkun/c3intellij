// This is a generated file. Not intended for manual editing.
package org.c3lang.intellij.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.c3lang.intellij.psi.C3Types.*;
import org.c3lang.intellij.psi.*;
import org.c3lang.intellij.stubs.C3GlobalNameStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class C3GlobalNameImpl extends C3GlobalNameMixinImpl implements C3GlobalName {

  public C3GlobalNameImpl(@NotNull ASTNode node) {
    super(node);
  }

  public C3GlobalNameImpl(@NotNull C3GlobalNameStub stub, @NotNull IStubElementType<?, ?> type) {
    super(stub, type);
  }

  public C3GlobalNameImpl(@NotNull C3GlobalNameStub stub, @Nullable IElementType type, @Nullable ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull C3Visitor visitor) {
    visitor.visitGlobalName(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof C3Visitor) accept((C3Visitor)visitor);
    else super.accept(visitor);
  }

}
