package org.c3lang.intellij.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProvider;
import org.c3lang.intellij.psi.C3BaseType;
import org.c3lang.intellij.psi.C3FuncHeader;
import org.c3lang.intellij.psi.C3FuncName;
import org.c3lang.intellij.psi.C3GlobalDecl;
import org.c3lang.intellij.psi.C3LocalDeclarationStmt;
import org.c3lang.intellij.psi.C3OptionalType;
import org.c3lang.intellij.psi.C3Parameter;
import org.c3lang.intellij.psi.C3StructMemberDeclaration;
import org.c3lang.intellij.psi.C3Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Classifies Find Usages results for C3 type references into meaningful groups
 * (method definition, return type, argument type, declaration) instead of "Unclassified".
 *
 * <p>A type usage's reference element is a {@link C3BaseType}; its syntactic role is determined
 * by walking up the (single) {@link C3Type} wrapper to the enclosing construct.</p>
 */
public final class C3UsageTypeProvider implements UsageTypeProvider
{
	private static final UsageType METHOD_DEFINITION = new UsageType(() -> "Method definition");
	private static final UsageType RETURNING = new UsageType(() -> "Returning");
	private static final UsageType ARGUMENT_PASSING = new UsageType(() -> "Argument passing");
	private static final UsageType DECLARATION = new UsageType(() -> "Declaration");
	private static final UsageType TYPE_USAGE = new UsageType(() -> "Type usage");

	@Override
	public @Nullable UsageType getUsageType(@NotNull PsiElement element)
	{
		if (element instanceof C3BaseType baseType) return classifyTypeUsage(baseType);
		return null;
	}

	private static @NotNull UsageType classifyTypeUsage(@NotNull C3BaseType baseType)
	{
		PsiElement parent = baseType.getParent();
		if (!(parent instanceof C3Type type)) return TYPE_USAGE;

		PsiElement context = type.getParent();

		// `fn ... Type.method(...)` — Type is the receiver in the function name.
		if (context instanceof C3FuncName) return METHOD_DEFINITION;

		// `(Type param)` — Type is a parameter type.
		if (context instanceof C3Parameter) return ARGUMENT_PASSING;

		// `Type field;` inside a struct/union body.
		if (context instanceof C3StructMemberDeclaration) return DECLARATION;

		// Wrapped in an optional type (`Type?`): return type, local or global declaration.
		if (context instanceof C3OptionalType optionalType)
		{
			PsiElement owner = optionalType.getParent();
			if (owner instanceof C3FuncHeader) return RETURNING;
			if (owner instanceof C3LocalDeclarationStmt || owner instanceof C3GlobalDecl) return DECLARATION;
		}

		return TYPE_USAGE;
	}
}
