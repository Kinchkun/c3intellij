package org.c3lang.intellij;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.psi.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * QA sweep: Go to Declaration on every identifier of a real workspace; prints what has no target,
 * by kind. Runs only when {@code C3_QA_WORKSPACE} names a directory with C3 sources (and
 * {@code C3_QA_STDLIB} the stdlib to index, default the Homebrew c3c); it never fails the build.
 */
public class C3NavigationSweepTest extends C3TestCase
{
	private final Map<String, List<String>> failures = new TreeMap<>();
	private int checked;

	private void addTree(Path root, String prefix) throws Exception
	{
		try (Stream<Path> files = Files.walk(root))
		{
			for (Path p : files.filter(x -> x.toString().endsWith(".c3") && !x.toString().contains("/build/")).toList())
			{
				myFixture.addFileToProject(prefix + root.relativize(p), Files.readString(p));
			}
		}
	}

	private static boolean isDeclarationName(PsiElement leaf)
	{
		PsiElement p = leaf.getParent();
		return p instanceof C3GlobalName || p instanceof C3AliasName || p instanceof C3TypeName || p instanceof C3FuncName
			|| p instanceof C3ForeachVar || p instanceof C3CatchUnwrap || p instanceof C3TryUnwrap || p instanceof C3VarDecl
			|| p instanceof C3MacroName || p instanceof C3LocalDeclAfterType || p instanceof C3Parameter || p instanceof C3EnumConstant
			|| p instanceof C3ConstdefConstant || p instanceof C3FaultDefinition || p instanceof C3ModulePath || p instanceof C3ImportPath
			|| p instanceof C3IdentifierList || p instanceof C3AttributeName || p instanceof C3Attribute || p instanceof C3StructMemberDeclaration
			|| p instanceof C3ConstDeclarationStmt || p instanceof C3Arg && ((C3Arg) p).getFirstChild() == leaf && p.getText().contains(":")
			|| PsiTreeUtil.getParentOfType(leaf, C3ModulePath.class, false) != null;
	}

	private void sweep(String fileName)
	{
		PsiFile file = myFixture.configureByFile(fileName);
		Document document = myFixture.getEditor().getDocument();
		for (LeafPsiElement leaf : PsiTreeUtil.collectElementsOfType(file, LeafPsiElement.class))
		{
			IElementType type = leaf.getElementType();
			if (type != C3Types.IDENT && type != C3Types.TYPE_IDENT && type != C3Types.CONST_IDENT && type != C3Types.AT_IDENT) continue;
			if (isDeclarationName(leaf)) continue;
			int offset = leaf.getTextOffset() + 1;
			checked++;
			PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(getProject(), myFixture.getEditor(), offset);
			if (targets.length > 0) continue;
			PsiReference ref = file.findReferenceAt(offset);
			PsiElement parent = leaf.getParent();
			PsiElement grand = parent != null ? parent.getParent() : null;
			String kind = type + " in " + (parent == null ? "?" : parent.getClass().getSimpleName()) + " < " + (grand == null ? "?" : grand.getClass().getSimpleName())
				+ (ref == null ? " [no reference]" : " [" + ref.getClass().getSimpleName() + " unresolved]");
			int line = document.getLineNumber(offset) + 1;
			String lineText = document.getText().substring(document.getLineStartOffset(line - 1), document.getLineEndOffset(line - 1)).trim();
			failures.computeIfAbsent(kind, k -> new ArrayList<>()).add(fileName + ":" + line + " '" + leaf.getText() + "' in: " + (lineText.length() > 90 ? lineText.substring(0, 90) + "…" : lineText));
		}
	}

	public void testSweep() throws Exception
	{
		String workspace = System.getenv("C3_QA_WORKSPACE");
		if (workspace == null || !Files.isDirectory(Path.of(workspace)))
		{
			System.out.println("[QA] skipped: set C3_QA_WORKSPACE to a directory with C3 sources");
			return;
		}
		String stdlib = System.getenv().getOrDefault("C3_QA_STDLIB", "/opt/homebrew/Cellar/c3c/0.8.5/lib/c3");
		if (Files.isDirectory(Path.of(stdlib))) addTree(Path.of(stdlib), "c3lib/");
		Path root = Path.of(workspace);
		addTree(root, "");
		List<String> files = new ArrayList<>();
		try (Stream<Path> all = Files.walk(root))
		{
			for (Path p : all.filter(x -> x.toString().endsWith(".c3") && !x.toString().contains("/build/") && !x.toString().contains("/lib/")).toList())
			{
				files.add(root.relativize(p).toString());
			}
		}
		for (String f : files) sweep(f);
		int total = failures.values().stream().mapToInt(List::size).sum();
		System.out.println("[QA] identifiers checked=" + checked + " without target=" + total);
		for (Map.Entry<String, List<String>> e : failures.entrySet())
		{
			System.out.println("[QA] " + e.getValue().size() + "x " + e.getKey());
			for (String s : e.getValue().subList(0, Math.min(4, e.getValue().size()))) System.out.println("[QA]     " + s);
		}
	}
}
