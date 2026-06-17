package org.c3lang.intellij.structure;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiFile;
import org.c3lang.intellij.psi.C3ConstdefDeclaration;
import org.c3lang.intellij.psi.C3EnumDeclaration;
import org.c3lang.intellij.psi.C3StructDeclaration;
import org.jetbrains.annotations.NotNull;

public final class C3StructureViewModel extends StructureViewModelBase
	implements StructureViewModel.ElementInfoProvider
{
	public C3StructureViewModel(@NotNull PsiFile psiFile)
	{
		super(psiFile, new C3StructureViewElement(psiFile));
	}

	@Override
	public Sorter @NotNull [] getSorters()
	{
		return new Sorter[]{ Sorter.ALPHA_SORTER };
	}

	@Override
	public boolean isAlwaysShowsPlus(StructureViewTreeElement element)
	{
		Object value = element.getValue();
		return value instanceof C3StructDeclaration
			|| value instanceof C3EnumDeclaration
			|| value instanceof C3ConstdefDeclaration;
	}

	@Override
	public boolean isAlwaysLeaf(StructureViewTreeElement element)
	{
		return false;
	}
}
