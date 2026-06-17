package org.c3lang.intellij.structure;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.C3Icons;
import org.c3lang.intellij.psi.C3AliasTypeDecl;
import org.c3lang.intellij.psi.C3AttrdefDecl;
import org.c3lang.intellij.psi.C3BitstructDeclaration;
import org.c3lang.intellij.psi.C3CallablePsiElement;
import org.c3lang.intellij.psi.C3CompoundStatement;
import org.c3lang.intellij.psi.C3ConstDeclarationStmt;
import org.c3lang.intellij.psi.C3ConstdefConstant;
import org.c3lang.intellij.psi.C3ConstdefDeclaration;
import org.c3lang.intellij.psi.C3EnumConstant;
import org.c3lang.intellij.psi.C3EnumDeclaration;
import org.c3lang.intellij.psi.C3FaultDefinition;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3MacroDefinition;
import org.c3lang.intellij.psi.C3PsiNamedElement;
import org.c3lang.intellij.psi.C3StructDeclaration;
import org.c3lang.intellij.psi.C3StructMemberDeclaration;
import org.c3lang.intellij.psi.C3TypeName;
import org.c3lang.intellij.psi.C3TypedefDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class C3StructureViewElement implements StructureViewTreeElement, SortableTreeElement
{
	private final PsiElement element;

	public C3StructureViewElement(@NotNull PsiElement element)
	{
		this.element = element;
	}

	@Override
	public Object getValue()
	{
		return element;
	}

	@Override
	public void navigate(boolean requestFocus)
	{
		if (element instanceof NavigatablePsiElement navigatable) navigatable.navigate(requestFocus);
	}

	@Override
	public boolean canNavigate()
	{
		return element instanceof NavigatablePsiElement navigatable && navigatable.canNavigate();
	}

	@Override
	public boolean canNavigateToSource()
	{
		return element instanceof NavigatablePsiElement navigatable && navigatable.canNavigateToSource();
	}

	@Override
	public @NotNull String getAlphaSortKey()
	{
		String label = label(element);
		return label != null ? label : "";
	}

	@Override
	public @NotNull ItemPresentation getPresentation()
	{
		String label = label(element);
		String text = label != null
			? label
			: (element instanceof PsiFile file ? file.getName() : element.getText());
		return new PresentationData(text, null, icon(element), null);
	}

	@Override
	public StructureViewTreeElement @NotNull [] getChildren()
	{
		List<PsiElement> children = new ArrayList<>();
		if (element instanceof PsiFile file)
		{
			collectTopLevel(file, children);
		}
		else
		{
			if (element instanceof C3StructDeclaration struct && struct.getStructBody() != null)
			{
				children.addAll(struct.getStructBody().getStructMemberDeclarationList());
			}
			else if (element instanceof C3EnumDeclaration enumDecl && enumDecl.getEnumList() != null)
			{
				children.addAll(enumDecl.getEnumList().getEnumConstantList());
			}
			else if (element instanceof C3ConstdefDeclaration)
			{
				children.addAll(PsiTreeUtil.findChildrenOfType(element, C3ConstdefConstant.class));
			}

			// Methods (fn/macro Type.name) are nested under their owning type declaration.
			String ownerType = declaredTypeName(element);
			if (ownerType != null)
			{
				children.addAll(methodsOf(ownerType, element.getContainingFile()));
			}
		}

		children.sort(Comparator.comparingInt(PsiElement::getTextOffset));
		StructureViewTreeElement[] result = new StructureViewTreeElement[children.size()];
		for (int i = 0; i < children.size(); i++)
		{
			result[i] = new C3StructureViewElement(children.get(i));
		}
		return result;
	}

	private static void collectTopLevel(@NotNull PsiFile file, @NotNull List<PsiElement> out)
	{
		Set<String> declaredTypes = fileDeclaredTypeNames(file);
		for (PsiElement el : PsiTreeUtil.findChildrenOfAnyType(file,
			C3FuncDef.class,
			C3MacroDefinition.class,
			C3StructDeclaration.class,
			C3EnumDeclaration.class,
			C3BitstructDeclaration.class,
			C3FaultDefinition.class,
			C3TypedefDecl.class,
			C3AliasTypeDecl.class,
			C3ConstDeclarationStmt.class,
			C3ConstdefDeclaration.class,
			C3AttrdefDecl.class))
		{
			// Skip declarations nested inside a function/macro body (e.g. local consts).
			if (PsiTreeUtil.getParentOfType(el, C3CompoundStatement.class) != null) continue;

			// A method whose receiver type is declared in this file is shown nested under
			// that type instead of at the top level.
			String receiver = methodReceiver(el);
			if (receiver != null && declaredTypes.contains(receiver)) continue;

			out.add(el);
		}
	}

	/** The receiver type name of a method ({@code fn/macro Type.name}), or null if not a method. */
	private static @Nullable String methodReceiver(@NotNull PsiElement element)
	{
		if (!(element instanceof C3CallablePsiElement callable) || callable.getType() == null) return null;
		String fqName = callable.getFqName().getName();
		int dot = fqName.indexOf('.');
		return dot > 0 ? fqName.substring(0, dot) : null;
	}

	private static @NotNull List<PsiElement> methodsOf(@Nullable String typeName, @NotNull PsiFile file)
	{
		List<PsiElement> result = new ArrayList<>();
		if (typeName == null) return result;
		for (PsiElement el : PsiTreeUtil.findChildrenOfAnyType(file, C3FuncDef.class, C3MacroDefinition.class))
		{
			if (typeName.equals(methodReceiver(el))) result.add(el);
		}
		return result;
	}

	private static @NotNull Set<String> fileDeclaredTypeNames(@NotNull PsiFile file)
	{
		Set<String> names = new HashSet<>();
		for (PsiElement el : PsiTreeUtil.findChildrenOfAnyType(file,
			C3StructDeclaration.class,
			C3EnumDeclaration.class,
			C3BitstructDeclaration.class,
			C3TypedefDecl.class,
			C3AliasTypeDecl.class,
			C3ConstdefDeclaration.class,
			C3FaultDefinition.class))
		{
			String name = declaredTypeName(el);
			if (name != null) names.add(name);
		}
		return names;
	}

	private static @Nullable String declaredTypeName(@NotNull PsiElement e)
	{
		if (e instanceof C3StructDeclaration s) return typeName(s.getTypeName());
		if (e instanceof C3EnumDeclaration en) return typeName(en.getTypeName());
		if (e instanceof C3BitstructDeclaration b) return typeName(b.getTypeName());
		if (e instanceof C3TypedefDecl t) return typeName(t.getTypeName());
		if (e instanceof C3AliasTypeDecl a) return typeName(a.getTypeName());
		if (e instanceof C3ConstdefDeclaration c) return typeName(c.getTypeName());
		if (e instanceof C3FaultDefinition f) return f.getName();
		return null;
	}

	private static @Nullable String label(@NotNull PsiElement e)
	{
		if (e instanceof C3FuncDef func) return func.getFqName().getName();
		if (e instanceof C3MacroDefinition macro) return macro.getFqName().getName();
		if (e instanceof C3StructDeclaration struct) return typeName(struct.getTypeName());
		if (e instanceof C3EnumDeclaration enumDecl) return typeName(enumDecl.getTypeName());
		if (e instanceof C3BitstructDeclaration bitstruct) return typeName(bitstruct.getTypeName());
		if (e instanceof C3TypedefDecl typedef) return typeName(typedef.getTypeName());
		if (e instanceof C3AliasTypeDecl alias) return typeName(alias.getTypeName());
		if (e instanceof C3ConstdefDeclaration constdef) return typeName(constdef.getTypeName());
		if (e instanceof C3PsiNamedElement named) return named.getName();
		if (e instanceof PsiFile file) return file.getName();
		return firstLine(e);
	}

	private static @Nullable Icon icon(@NotNull PsiElement e)
	{
		if (e instanceof C3FuncDef || e instanceof C3MacroDefinition) return AllIcons.Nodes.Method;
		if (e instanceof C3StructDeclaration || e instanceof C3BitstructDeclaration) return AllIcons.Nodes.Class;
		if (e instanceof C3EnumDeclaration) return AllIcons.Nodes.Enum;
		if (e instanceof C3FaultDefinition) return AllIcons.Nodes.Class;
		if (e instanceof C3TypedefDecl || e instanceof C3AliasTypeDecl) return AllIcons.Nodes.Class;
		if (e instanceof C3AttrdefDecl) return AllIcons.Nodes.Annotationtype;
		if (e instanceof C3ConstDeclarationStmt
			|| e instanceof C3ConstdefDeclaration
			|| e instanceof C3ConstdefConstant) return AllIcons.Nodes.Constant;
		if (e instanceof C3EnumConstant || e instanceof C3StructMemberDeclaration) return AllIcons.Nodes.Field;
		if (e instanceof PsiFile) return C3Icons.FILE;
		return null;
	}

	private static @Nullable String typeName(@Nullable C3TypeName typeName)
	{
		return typeName != null ? typeName.getName() : null;
	}

	private static @NotNull String firstLine(@NotNull PsiElement e)
	{
		String text = e.getText();
		int end = text.length();
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (c == '{' || c == '\n' || c == '\r')
			{
				end = i;
				break;
			}
		}
		return text.substring(0, end).trim();
	}
}
