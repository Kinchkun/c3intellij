package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/** Verifies the gutter run buttons for tests: per test function and for all tests of a module. */
public class C3TestLineMarkerTest extends BasePlatformTestCase
{
	private List<String> gutterTooltips(String text)
	{
		myFixture.configureByText("t.c3", text);
		return myFixture.findAllGutters().stream().map(GutterMark::getTooltipText).toList();
	}

	public void testModuleLevelTestAttributeMarksEveryFunction()
	{
		List<String> tooltips = gutterTooltips("module app::tests @test;\nfn void a() {}\nfn void b() {}\n");
		assertTrue(tooltips.toString(), tooltips.contains("Run tests in 'app::tests'"));
		assertTrue(tooltips.toString(), tooltips.contains("Run test 'a'"));
		assertTrue(tooltips.toString(), tooltips.contains("Run test 'b'"));
	}

	public void testFunctionLevelTestAttribute()
	{
		List<String> tooltips = gutterTooltips("module app;\nfn void helper() {}\nfn void a() @test {}\n");
		assertTrue(tooltips.toString(), tooltips.contains("Run tests in 'app'"));
		assertTrue(tooltips.toString(), tooltips.contains("Run test 'a'"));
		assertFalse(tooltips.toString(), tooltips.contains("Run test 'helper'"));
	}

	public void testNoModuleButtonWithoutTests()
	{
		List<String> tooltips = gutterTooltips("module app;\nfn void helper() {}\n");
		assertTrue(tooltips.toString(), tooltips.isEmpty());
	}
}
