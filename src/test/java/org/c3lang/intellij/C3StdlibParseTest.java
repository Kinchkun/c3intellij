package org.c3lang.intellij;

import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Every file of the C3 standard library parses without errors: the stdlib uses the language's
 * newest syntax, so this keeps the grammar in step with the compiler. The stdlib is read from
 * $C3_STDLIB, else from mise's c3c 0.8.5 install; without either the test is skipped.
 */
public class C3StdlibParseTest extends C3TestCase
{
	private static Path stdlib()
	{
		String given = System.getenv("C3_STDLIB");
		if (given != null && !given.isEmpty()) return Paths.get(given);
		return Paths.get(System.getProperty("user.home"), ".local/share/mise/installs/github-c3lang-c3c/0.8.5/lib/std");
	}

	public void testTheStandardLibraryParses() throws Exception
	{
		Path root = stdlib();
		if (!Files.isDirectory(root))
		{
			System.out.println("[C3StdlibParseTest] skipped: no stdlib at " + root + " (set C3_STDLIB)");
			return;
		}
		List<Path> files;
		try (Stream<Path> s = Files.walk(root))
		{
			files = s.filter(p -> p.toString().endsWith(".c3")).sorted().toList();
		}
		List<String> errors = new ArrayList<>();
		for (Path p : files)
		{
			String text = Files.readString(p);
			PsiFile file = PsiFileFactory.getInstance(getProject())
				.createFileFromText(p.getFileName().toString(), C3Language.INSTANCE, text);
			for (PsiErrorElement e : PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class))
			{
				int line = (int) text.substring(0, Math.min(e.getTextOffset(), text.length())).chars().filter(c -> c == '\n').count() + 1;
				errors.add(root.relativize(p) + ":" + line + ": " + e.getErrorDescription());
			}
		}
		assertTrue(files.size() + " files, " + errors.size() + " parse errors:\n"
			+ String.join("\n", errors.subList(0, Math.min(errors.size(), 50))), errors.isEmpty());
	}
}
