package org.c3lang.intellij;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/** A workspace (monorepo) holding several C3 projects, like c3projects/{c3script,mystd}. */
public class C3ProjectsTest extends BasePlatformTestCase
{
	private VirtualFile script;
	private VirtualFile stdArgs;
	private VirtualFile stdTest;

	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		myFixture.addFileToProject("README.md", "# c3projects");
		myFixture.addFileToProject("c3script/project.json", "{}");
		script = myFixture.addFileToProject("c3script/src/main.c3", "module c3script;").getVirtualFile();
		myFixture.addFileToProject("c3script/build/project.json", "{}"); // build output is ignored
		myFixture.addFileToProject("mystd/project.json", "{}");
		stdArgs = myFixture.addFileToProject("mystd/mystd.c3l/src/args/args.c3", "module mystd::args;").getVirtualFile();
		stdTest = myFixture.addFileToProject("mystd/test/args_test.c3", "module mystd::args_test;").getVirtualFile();
	}

	public void testFindsOwningProjectByNearestManifest()
	{
		assertEquals("c3script", C3Projects.findProjectDir(script).getName());
		assertEquals("mystd", C3Projects.findProjectDir(stdArgs).getName());
		assertEquals("mystd", C3Projects.findProjectDir(stdTest).getName());
	}

	public void testFindsAllProjectsOfTheWorkspace()
	{
		List<String> names = C3Projects.findAll(getProject()).stream().map(VirtualFile::getName).sorted().toList();
		assertEquals(List.of("c3script", "mystd"), names);
	}

	public void testConsoleLinkPrefersMatchingPath()
	{
		VirtualFile otherMain = myFixture.addFileToProject("mystd/src/main.c3", "module x;").getVirtualFile();
		assertEquals(script, C3ConsoleFilter.bestMatch(List.of(otherMain, script), "c3script/src/main.c3"));
		assertEquals(stdArgs, C3ConsoleFilter.bestMatch(List.of(stdArgs), "../mystd/mystd.c3l/src/args/args.c3"));
		assertEquals(otherMain, C3ConsoleFilter.bestMatch(List.of(script, otherMain), "mystd/src/main.c3"));
	}
}
