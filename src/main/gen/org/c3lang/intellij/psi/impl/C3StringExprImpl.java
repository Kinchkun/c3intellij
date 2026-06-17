// This is a generated file. Not intended for manual editing.
package org.c3lang.intellij.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.util.PsiTreeUtil;
import static org.c3lang.intellij.psi.C3Types.*;
import org.c3lang.intellij.psi.*;

// NOTE: the PsiLanguageInjectionHost implementation below is a post-generation patch
// (string_expr has no grammar mixin). Re-apply it after regenerating — see NOTES.md.
public class C3StringExprImpl extends C3ExprImpl implements C3StringExpr, PsiLanguageInjectionHost {

  public C3StringExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull C3Visitor visitor) {
    visitor.visitStringExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof C3Visitor) accept((C3Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  public boolean isValidHost() {
    String text = getText();
    return text.length() >= 2 && (text.charAt(0) == '"' || text.charAt(0) == '`');
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    ASTNode child = getNode().getFirstChildNode();
    if (child instanceof LeafElement leaf) {
      leaf.replaceWithText(text);
    }
    return this;
  }

  @Override
  public @NotNull LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new LiteralTextEscaper<>(this) {
      @Override
      public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
        outChars.append(myHost.getText(), rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
        return true;
      }

      @Override
      public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
        return Math.min(rangeInsideHost.getStartOffset() + offsetInDecoded, rangeInsideHost.getEndOffset());
      }

      @Override
      public boolean isOneLine() {
        return false;
      }
    };
  }

}
