package org.c3lang.intellij;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.c3lang.intellij.findUsages.C3UsageTypeProvider;
import org.c3lang.intellij.psi.C3BaseType;

import java.util.HashSet;
import java.util.Set;

public class C3UsageTypeTest extends BasePlatformTestCase
{
	public void testTypeUsageClassification()
	{
		PsiFile file = myFixture.configureByText("a.c3",
			"module app;\n" +
			"struct Foo { int x; }\n" +
			"fn Foo make() { Foo v; return v; }\n" +
			"fn void Foo.use(&self) {}\n" +
			"fn void take(Foo f) {}\n");

		C3UsageTypeProvider provider = new C3UsageTypeProvider();
		Set<String> labels = new HashSet<>();
		for (C3BaseType baseType : PsiTreeUtil.findChildrenOfType(file, C3BaseType.class))
		{
			if (!"Foo".equals(baseType.getText())) continue;
			var usageType = provider.getUsageType(baseType);
			assertNotNull(usageType);
			labels.add(usageType.toString());
		}
		System.out.println("[TEST] type usage labels = " + labels);
		assertTrue("missing Returning: " + labels, labels.contains("Returning"));
		assertTrue("missing Method definition: " + labels, labels.contains("Method definition"));
		assertTrue("missing Argument passing: " + labels, labels.contains("Argument passing"));
		assertTrue("missing Declaration: " + labels, labels.contains("Declaration"));
	}
}
