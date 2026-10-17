package org.c3lang.intellij.psi;

import com.intellij.psi.StubBasedPsiElement;
import org.c3lang.intellij.stubs.C3FuncDefStub;

/**
 * The hand-written "mixin" interface for the {@code func_def} grammar rule.
 *
 * <p><strong>The mixin pattern in one paragraph:</strong> Grammar-Kit generates a PSI interface
 * {@code C3FuncDef} and impl {@code C3FuncDefImpl} from {@code C3.bnf}. Those files are regenerated
 * and must not be edited. To attach our own behaviour we declare two directives in {@code C3.bnf}:
 * {@code implements("func_def")=...C3FuncDefMixin} and {@code mixin("func_def")=...C3FuncDefMixinImpl}.
 * The generated interface then {@code extends C3FuncDefMixin} and the generated impl
 * {@code extends C3FuncDefMixinImpl} – so any method we put here (and implement in the impl) becomes
 * part of every {@code C3FuncDef} without us touching generated code.
 *
 * <p>This interface composes the contracts a function definition must fulfil: it is callable, it is a
 * stub-backed element ({@link StubBasedPsiElement} of {@link C3FuncDefStub}), it has a name
 * ({@link C3PsiNamedElement}), and it exposes its identifier ({@link C3NameIdentProvider}).
 */
public interface C3FuncDefMixin extends C3CallablePsiElement, StubBasedPsiElement<C3FuncDefStub>, C3PsiNamedElement, C3NameIdentProvider
{
}
