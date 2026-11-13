package org.c3lang.intellij.navigation;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.C3AttributeNames;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3FuncName;
import org.c3lang.intellij.psi.C3InterfaceDefinition;
import org.c3lang.intellij.psi.C3TypeName;
import org.c3lang.intellij.psi.C3Types;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Gutter icons for the implementation relation, as for Kotlin and Java:
 * <ul>
 *   <li>an interface with implementers and an interface method with implementing methods get the
 *   "is implemented" icon, which navigates to them;</li>
 *   <li>a {@code @dynamic} method that implements an interface method gets the "implements" icon,
 *   which navigates to the interface method.</li>
 * </ul>
 * The lookups read the stub index and resolve type names, so they run in the slow pass.
 */
public final class C3ImplementationLineMarkerProvider implements LineMarkerProvider
{
	@Override
	public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element)
	{
		return null;
	}

	@Override
	public void collectSlowLineMarkers(
		@NotNull List<? extends PsiElement> elements,
		@NotNull Collection<? super LineMarkerInfo<?>> result)
	{
		for (PsiElement element : elements)
		{
			ProgressManager.checkCanceled();
			// Line markers are anchored on leaves: the name token of the interface or of the function.
			if (element.getNode() == null) continue;

			LineMarkerInfo<?> marker = null;
			if (element.getNode().getElementType() == C3Types.TYPE_IDENT && element.getParent() instanceof C3TypeName typeName)
			{
				marker = interfaceMarker(element, typeName);
			}
			else if (element.getNode().getElementType() == C3Types.IDENT && element.getParent() instanceof C3FuncName)
			{
				C3FuncDef method = PsiTreeUtil.getParentOfType(element, C3FuncDef.class);
				if (method != null) marker = methodMarker(element, method);
			}
			if (marker != null) result.add(marker);
		}
	}

	private static @Nullable LineMarkerInfo<?> interfaceMarker(@NotNull PsiElement leaf, @NotNull C3TypeName typeName)
	{
		if (C3Implementations.interfaceOf(typeName) == null || !C3Implementations.hasImplementers(typeName)) return null;

		String name = typeName.getName();
		return NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
			.setTargets(NotNullLazyValue.lazy(() -> C3Implementations.implementers(typeName)))
			.setTooltipText("Go to implementations of '" + name + "'")
			.setPopupTitle("Implementations of " + name)
			.createLineMarkerInfo(leaf);
	}

	private static @Nullable LineMarkerInfo<?> methodMarker(@NotNull PsiElement leaf, @NotNull C3FuncDef method)
	{
		String name = method.getName();
		C3InterfaceDefinition declaringInterface = C3Implementations.declaringInterface(method);
		if (declaringInterface != null)
		{
			if (C3Implementations.implementingMethods(method).isEmpty()) return null;

			String qualifiedName = declaringInterface.getTypeName().getName() + "." + name;
			return NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
				.setTargets(NotNullLazyValue.lazy(() -> C3Implementations.implementingMethods(method)))
				.setTooltipText("Go to implementations of '" + qualifiedName + "'")
				.setPopupTitle("Implementations of " + qualifiedName)
				.createLineMarkerInfo(leaf);
		}

		// Only a @dynamic method implements an interface method.
		if (!C3AttributeNames.has(method.getAttributes(), "dynamic")) return null;

		List<C3FuncDef> interfaceMethods = C3Implementations.implementedInterfaceMethods(method);
		if (interfaceMethods.isEmpty()) return null;

		C3InterfaceDefinition implemented = C3Implementations.declaringInterface(interfaceMethods.get(0));
		String interfaceName = implemented != null ? implemented.getTypeName().getName() : null;
		return NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementingMethod)
			.setTargets(NotNullLazyValue.lazy(() -> C3Implementations.implementedInterfaceMethods(method)))
			.setTooltipText("Implements '" + name + "' of interface '" + interfaceName + "'")
			.setPopupTitle("Interface Methods of " + name)
			.createLineMarkerInfo(leaf);
	}
}
