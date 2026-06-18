package org.c3lang.intellij.docs;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3AliasTypeDecl;
import org.c3lang.intellij.psi.C3NameIdentProvider;
import org.c3lang.intellij.psi.C3AttrdefDecl;
import org.c3lang.intellij.psi.C3BitstructDeclaration;
import org.c3lang.intellij.psi.C3ConstDeclarationStmt;
import org.c3lang.intellij.psi.C3ConstdefConstant;
import org.c3lang.intellij.psi.C3ConstdefDeclaration;
import org.c3lang.intellij.psi.C3EnumConstant;
import org.c3lang.intellij.psi.C3EnumDeclaration;
import org.c3lang.intellij.psi.C3FaultDefinition;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3InterfaceDefinition;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3MacroDefinition;
import org.c3lang.intellij.psi.C3Module;
import org.c3lang.intellij.psi.C3StructDeclaration;
import org.c3lang.intellij.psi.C3TypedefDecl;
import org.jetbrains.annotations.Nullable;

public final class C3DocumentationProvider extends AbstractDocumentationProvider
{
	@Override
	public @Nullable String generateDoc(@Nullable PsiElement element, @Nullable PsiElement originalElement)
	{
		// Type references resolve to the C3TypeName definition element; document the
		// enclosing declaration (struct / enum / typedef / ...) instead.
		if (element instanceof org.c3lang.intellij.psi.C3TypeName)
		{
			element = element.getParent();
		}
		if (element instanceof C3FuncDef)
		{
			return FuncDefinitionDocs.generateFuncDefDoc((C3FuncDef) element);
		}
		if (element instanceof C3MacroDefinition)
		{
			return MacroDefinitionDocs.generateMacroDefinitionDoc((C3MacroDefinition) element);
		}
		if (element instanceof C3LocalDeclAfterType)
		{
			return VarDeclDocs.generateVarDeclDoc((C3LocalDeclAfterType) element);
		}
		if (element instanceof C3ConstDeclarationStmt)
		{
			return ConstDeclDocs.generateConstDeclDoc((C3ConstDeclarationStmt) element);
		}
		if (element instanceof C3EnumConstant)
		{
			return TypeDeclarationDocs.generateEnumConstantDoc((C3EnumConstant) element);
		}
		if (element instanceof C3Module)
		{
			return TypeDeclarationDocs.generateModuleDoc((C3Module) element);
		}
		if (element instanceof C3StructDeclaration
			|| element instanceof C3EnumDeclaration
			|| element instanceof C3BitstructDeclaration
			|| element instanceof C3TypedefDecl
			|| element instanceof C3AliasTypeDecl
			|| element instanceof C3AttrdefDecl
			|| element instanceof C3ConstdefDeclaration
			|| element instanceof C3ConstdefConstant
			|| element instanceof C3InterfaceDefinition
			|| element instanceof C3FaultDefinition)
		{
			return TypeDeclarationDocs.generateDeclarationDoc((org.c3lang.intellij.psi.C3PsiElement) element);
		}
		return null;
	}

	@Override
	public @Nullable String generateHoverDoc(@Nullable PsiElement element, @Nullable PsiElement originalElement)
	{
		return generateDoc(element, originalElement);
	}

	@Override
	public @Nullable PsiElement getCustomDocumentationElement(
		@Nullable Editor editor, @Nullable PsiFile file, @Nullable PsiElement contextElement, int targetOffset)
	{
		// When Quick Doc is invoked on a declaration's name (not a reference), the caret element
		// is a leaf; map it to the enclosing declaration that generateDoc understands.
		if (contextElement == null) return null;

		// A type usage (e.g. `Formatter` in a `Formatter*` parameter, or the receiver type of a
		// method name) is a bare TYPE_IDENT inside a C3BaseType — not a C3TypeName — and carries its
		// own reference to the type's definition. Don't hijack it with the enclosing declaration;
		// return null so the platform resolves that reference to the type itself.
		if (contextElement.getNode() != null
			&& contextElement.getNode().getElementType() == org.c3lang.intellij.psi.C3Types.TYPE_IDENT
			&& PsiTreeUtil.getParentOfType(contextElement, org.c3lang.intellij.psi.C3TypeName.class) == null)
		{
			return null;
		}

		PsiElement declaration = PsiTreeUtil.getNonStrictParentOfType(contextElement,
			C3FuncDef.class,
			C3MacroDefinition.class,
			C3EnumConstant.class,
			C3ConstdefConstant.class,
			C3FaultDefinition.class,
			C3AttrdefDecl.class,
			C3Module.class,
			C3ConstDeclarationStmt.class,
			C3LocalDeclAfterType.class);
		if (declaration != null)
		{
			// Map to the declaration only when the caret is on its name. These declarations enclose
			// their initializer (e.g. `String s = load_environment(...)`), so a caret on a reference
			// in the initializer must resolve that reference, not document the variable.
			PsiElement nameElement = declarationName(declaration);
			if (nameElement == null || PsiTreeUtil.isAncestor(nameElement, contextElement, false))
			{
				return declaration;
			}
			return null;
		}

		// Only handle a type name when it *is* the name of a declaration. A type name used as a
		// reference (e.g. the interface in `struct X (Printable)`) must resolve via its reference
		// instead, so return null and let the platform use that.
		org.c3lang.intellij.psi.C3TypeName typeName =
			PsiTreeUtil.getNonStrictParentOfType(contextElement, org.c3lang.intellij.psi.C3TypeName.class);
		return typeName != null && isTypeDeclarationName(typeName) ? typeName : null;
	}

	/** The name element of a declaration, or null if it cannot be determined. */
	private static @Nullable PsiElement declarationName(@org.jetbrains.annotations.NotNull PsiElement declaration)
	{
		if (declaration instanceof C3NameIdentProvider provider) return provider.getNameIdentElement();
		if (declaration instanceof PsiNameIdentifierOwner owner) return owner.getNameIdentifier();
		return null;
	}

	private static boolean isTypeDeclarationName(@org.jetbrains.annotations.NotNull org.c3lang.intellij.psi.C3TypeName typeName)
	{
		PsiElement parent = typeName.getParent();
		return parent instanceof C3StructDeclaration
			|| parent instanceof C3EnumDeclaration
			|| parent instanceof C3BitstructDeclaration
			|| parent instanceof C3TypedefDecl
			|| parent instanceof C3AliasTypeDecl
			|| parent instanceof C3ConstdefDeclaration
			|| parent instanceof C3InterfaceDefinition;
	}
}
