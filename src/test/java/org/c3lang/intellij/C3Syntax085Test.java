package org.c3lang.intellij;

import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Syntax added or renamed up to C3 0.8.5 (see c3c's releasenotes.md) parses without errors:
 * the new declaration keywords (constset, excuse, attrmacro) next to the old ones, $feat with
 * the expression syntax of @feat, @feat itself, a ternary in an alias, @tag on parameters,
 * a.$field / a.$eval($field), $vaarg[^1].
 */
public class C3Syntax085Test extends C3TestCase
{
	private void assertParses(@NotNull String code)
	{
		PsiFile file = myFixture.configureByText("syntax.c3", "module syntax;\n" + code + "\n");
		Collection<PsiErrorElement> errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class);
		String found = errors.stream()
			.map(e -> e.getErrorDescription() + " at '" + e.getParent().getText() + "'")
			.collect(Collectors.joining("\n"));
		assertTrue("parse errors in:\n" + code + "\n" + found, errors.isEmpty());
	}

	public void testConstsetAndConstdef()
	{
		assertParses("constset Color : int { RED = 1, BLUE = 2 }");
		assertParses("constset Errno : inline CInt @feat(DARWIN) { EPERM = 1 }");
		assertParses("constdef Color : int { RED = 1, BLUE = 2 }");
	}

	public void testExcuseAndFaultdef()
	{
		assertParses("excuse NOT_FOUND, TOO_LONG;");
		assertParses("faultdef NOT_FOUND;");
	}

	public void testAttrmacroAndAttrdef()
	{
		assertParses("attrmacro @Hot = @inline;");
		assertParses("attrdef @Hot = @inline;");
	}

	public void testFeat()
	{
		assertParses("const X = $feat(FOO);");
		assertParses("const X = $feat(FOO & !BAR);");
		assertParses("const X = $feat(FOO | BAR & !BAZ);");
		assertParses("const X = $feature(FOO);");
		assertParses("fn void f() @feat(FOO) {}");
		assertParses("fn void f() @feat(!WIN32 & !DARWIN & !BSD) {}");
		assertParses("fn void g() { $if $feat(FOO): int x; $endif }");
	}

	public void testAliasTernary()
	{
		assertParses("fn void a() {}\nfn void b() {}\nalias c = $feat(FOO) ? a : b;");
	}

	public void testParameterTag()
	{
		assertParses("fn void f(int x @tag(\"k\", 1)) {}");
	}

	public void testCompileTimeFieldAccess()
	{
		assertParses("macro m(a) { $foreach $field : $typeof(a).membersof: a.$field = 0; a.$eval($field) = 1; $endforeach }");
	}

	public void testBracedExcuse()
	{
		assertParses("excuse { RESULT_ERROR, RESULT_OK }");
		assertParses("excuse\n{\n\tCORRUPTED_DATA,\n\tIO_ERROR,\n}");
	}

	public void testStdlibConstructs()
	{
		assertParses("fn double f(double x) { double z = (1. - x) * 0.5; return 2. * z; }");
		assertParses("fn void f() { int[3] a; int[] s = a[0..1]; }");
		assertParses("import std::gfx::x11 @public, std::gfx::egl @public, std::io;");
		assertParses("extern const CFStringRef KCF_RUNLOOP_DEFAULT_MODE @cname(\"kCFRunLoopDefaultMode\");");
		assertParses("typedef SimdVec @local = ulong[<4>] @simd;");
		assertParses("alias KeyFnReturnType @if(!NO_KEY_FN) = KeyFn::returns;");
		assertParses("fn Allocation[] Tracking.list(&self) => self.mutex.@in_lock()\n{\n\treturn self.map.tvalues();\n}");
		assertParses("fn void wake() => objc::@autoreleasepool()\n{\n\treturn;\n}");
		assertParses("macro m() { $ext +++= \"VK_KHR_xlib_surface\"; }");
		assertParses("enum Win32_MEM_EXTENDED_PARAMETER_TYPE : CInt\n{\n\tINVALID_TYPE,\n\tMAX\n}\nalias Win32_PMEM = Win32_MEM_EXTENDED_PARAMETER_TYPE;");
	}

	public void testVaargFromTheEnd()
	{
		assertParses("macro m(...) { var x = $vaarg[^1]; }");
	}
}
