package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3TypeName;

import java.util.Arrays;
import java.util.List;

/**
 * Go to Implementation and the gutter icons for interfaces: from an interface to the types that
 * implement it, from an interface method to the implementing methods, and back.
 */
public class C3InterfaceImplementationsTest extends C3TestCase
{
	private static final String SHAPES =
		"module shapes;\n"
			+ "interface Shape\n{\n    fn double area();\n    fn String name() @optional;\n}\n"
			+ "interface Solid : Shape\n{\n    fn double volume();\n}\n";

	private static final String CIRCLE =
		"module shapes::round;\nimport shapes;\n"
			+ "struct Circle (Shape) { double radius; }\n"
			+ "fn double Circle.area(&self) @dynamic => 3.14 * self.radius * self.radius;\n"
			+ "fn void Circle.grow(&self) { self.radius *= 2; }\n";

	private static final String BOX =
		"module shapes::boxes;\nimport shapes;\n"
			+ "struct Box (shapes::Solid) { double side; }\n"
			+ "fn double Box.area(&self) @dynamic => 6 * self.side * self.side;\n"
			+ "fn double Box.volume(&self) @dynamic => self.side * self.side * self.side;\n"
			+ "typedef Meters (Shape) = double;\n"
			+ "fn double Meters.area(&self) @dynamic => 0;\n";

	/** A same-named interface of another module, with its own implementer. */
	private static final String OTHER =
		"module drawing;\n"
			+ "interface Shape\n{\n    fn double area();\n}\n"
			+ "struct Stroke (Shape) { int width; }\n"
			+ "fn double Stroke.area(&self) @dynamic => 0;\n";

	private void addLibrary()
	{
		myFixture.addFileToProject("circle.c3", CIRCLE);
		myFixture.addFileToProject("box.c3", BOX);
		myFixture.addFileToProject("other.c3", OTHER);
	}

	private List<String> implementations()
	{
		GotoTargetHandler.GotoData data = CodeInsightTestUtil.gotoImplementation(myFixture.getEditor(), myFixture.getFile());
		assertNotNull("no source element at the caret", data);
		return Arrays.stream(data.targets).map(C3InterfaceImplementationsTest::describe).sorted().toList();
	}

	private static String describe(PsiElement element)
	{
		if (element instanceof C3TypeName typeName) return typeName.getFqName().getFullName();
		if (element instanceof C3FuncDef method) return method.getFqName().getFullName();
		return element.toString();
	}

	private List<String> gutterTooltips()
	{
		return myFixture.findAllGutters().stream().map(GutterMark::getTooltipText).toList();
	}

	public void testInterfaceDeclarationFindsImplementers()
	{
		addLibrary();
		myFixture.configureByText("shapes.c3", SHAPES.replace("interface Shape", "interface Sha<caret>pe"));
		assertEquals(
			List.of("shapes::Solid", "shapes::boxes::Box", "shapes::boxes::Meters", "shapes::round::Circle"),
			implementations());
	}

	public void testSubInterfaceFindsOnlyItsImplementers()
	{
		addLibrary();
		myFixture.configureByText("shapes.c3", SHAPES.replace("interface Solid", "interface Sol<caret>id"));
		assertEquals(List.of("shapes::boxes::Box"), implementations());
	}

	public void testSameNamedInterfaceOfAnotherModule()
	{
		myFixture.addFileToProject("shapes.c3", SHAPES);
		myFixture.addFileToProject("circle.c3", CIRCLE);
		myFixture.addFileToProject("box.c3", BOX);
		myFixture.configureByText("other.c3", OTHER.replace("interface Shape", "interface Sha<caret>pe"));
		assertEquals(List.of("drawing::Stroke"), implementations());
	}

	public void testInterfaceMethodFindsImplementingMethods()
	{
		addLibrary();
		myFixture.configureByText("shapes.c3", SHAPES.replace("double area()", "double ar<caret>ea()"));
		assertEquals(
			List.of("shapes::boxes::Box.area", "shapes::boxes::Meters.area", "shapes::round::Circle.area"),
			implementations());
	}

	public void testInterfaceUsageIncludesTheInterfaceItself()
	{
		addLibrary();
		myFixture.addFileToProject("shapes.c3", SHAPES);
		myFixture.configureByText("main.c3", "module app;\nimport shapes;\nfn void draw(Sol<caret>id solid) {}\n");
		assertEquals(List.of("shapes::Solid", "shapes::boxes::Box"), implementations());
	}

	public void testInterfaceWithoutImplementersNavigatesToItself()
	{
		myFixture.configureByText("main.c3", "module app;\ninterface Lonely { fn void f(); }\nfn void use(Lon<caret>ely value) {}\n");
		assertEquals(List.of("app::Lonely"), implementations());
	}

	public void testGutterIconsOnInterface()
	{
		addLibrary();
		myFixture.configureByText("shapes.c3", SHAPES);
		List<String> tooltips = gutterTooltips();
		assertTrue(tooltips.toString(), tooltips.contains("Go to implementations of 'Shape'"));
		assertTrue(tooltips.toString(), tooltips.contains("Go to implementations of 'Solid'"));
		assertTrue(tooltips.toString(), tooltips.contains("Go to implementations of 'Shape.area'"));
		assertTrue(tooltips.toString(), tooltips.contains("Go to implementations of 'Solid.volume'"));
		// Nobody implements the optional method.
		assertFalse(tooltips.toString(), tooltips.contains("Go to implementations of 'Shape.name'"));
	}

	public void testGutterIconsOnImplementingMethods()
	{
		myFixture.addFileToProject("shapes.c3", SHAPES);
		myFixture.configureByText("box.c3", BOX);
		List<String> tooltips = gutterTooltips();
		// Box.area implements the method of the parent interface of Solid.
		assertTrue(tooltips.toString(), tooltips.contains("Implements 'area' of interface 'Shape'"));
		assertTrue(tooltips.toString(), tooltips.contains("Implements 'volume' of interface 'Solid'"));
		assertEquals(tooltips.toString(), 3, tooltips.size());
	}

	public void testNoGutterIconWithoutInterface()
	{
		myFixture.addFileToProject("shapes.c3", SHAPES);
		myFixture.configureByText("circle.c3", CIRCLE);
		List<String> tooltips = gutterTooltips();
		assertEquals(tooltips.toString(), List.of("Implements 'area' of interface 'Shape'"), tooltips);
	}

	public void testNoGutterIconOnInterfaceWithoutImplementers()
	{
		myFixture.configureByText("main.c3", "module app;\ninterface Lonely { fn void f(); }\n");
		assertTrue(gutterTooltips().toString(), gutterTooltips().isEmpty());
	}
}
