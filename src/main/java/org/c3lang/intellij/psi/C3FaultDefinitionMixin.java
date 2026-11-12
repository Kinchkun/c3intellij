package org.c3lang.intellij.psi;

import com.intellij.psi.StubBasedPsiElement;
import org.c3lang.intellij.stubs.C3FaultDefinitionStub;

public interface C3FaultDefinitionMixin extends StubBasedPsiElement<C3FaultDefinitionStub>, C3PsiNamedElement, C3NameIdentProvider, C3FullyQualifiedNamePsiElement, C3BuiltinPsiElement
{
}
