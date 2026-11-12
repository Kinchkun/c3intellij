package org.c3lang.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public interface C3CallablePsiElement extends C3FullyQualifiedNamePsiElement, C3BuiltinPsiElement
{
	@NotNull String getSourceFileName();
	@Nullable ShortType getType();
	@Nullable ShortType getReturnType();
	@NotNull List<ParamType> getParameterTypes();
}
