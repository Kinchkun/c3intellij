package org.c3lang.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ProcessingContext;
import org.c3lang.intellij.C3Icons;
import org.c3lang.intellij.index.NameIndex;
import org.c3lang.intellij.intention.AddImportQuickFix;
import org.c3lang.intellij.psi.C3ModuleDefinition;
import org.c3lang.intellij.psi.C3PsiElement;
import org.c3lang.intellij.psi.C3TypeFullyQualifiedNamePsiElement;
import org.c3lang.intellij.psi.C3TypeName;
import org.c3lang.intellij.psi.C3Types;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.c3lang.intellij.psi.ModuleName;
import org.c3lang.intellij.stubs.C3TypeEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Completes interface names inside the interface-implementation list of a declaration, e.g.
 * {@code struct MyStruct (Printable, Other)}. Only interfaces are offered.
 *
 * <p>The context is detected by scanning tokens backwards rather than relying on a parsed
 * {@code C3InterfaceImpl}, because while the declaration is still being typed (no body yet) the
 * surrounding code does not parse and there is no {@code C3InterfaceImpl} PSI node.
 */
public final class InterfaceCompletionContributor extends CompletionProvider<CompletionParameters>
{
	public static final InterfaceCompletionContributor INSTANCE = new InterfaceCompletionContributor();

	private InterfaceCompletionContributor()
	{
	}

	@Override
	protected void addCompletions(
		@NotNull CompletionParameters parameters,
		@NotNull ProcessingContext context,
		@NotNull CompletionResultSet result)
	{
		if (!inInterfaceImplContext(parameters.getPosition())
			&& !inInterfaceImplContext(parameters.getOriginalPosition()))
		{
			return;
		}

		// While the declaration is incomplete the struct tokens may sit outside the module section in
		// the PSI, so getModuleDefinition() can be null; fall back to the file's module section. A
		// null module simply means no same-module check / no import insertion.
		C3ModuleDefinition moduleDefinition = CompletionExtensionsKt.getModuleDefinition(parameters);
		if (moduleDefinition == null)
		{
			moduleDefinition = PsiTreeUtil.findChildOfType(parameters.getOriginalFile(), C3ModuleDefinition.class);
		}

		var project = parameters.getPosition().getProject();
		ModuleName moduleName = moduleDefinition != null ? moduleDefinition.getModuleName() : null;

		// In this error-state context the platform's prefix can include the inserted dummy token,
		// which would silently drop every suggestion. Set the prefix to exactly what was typed.
		CompletionResultSet results = result.withPrefixMatcher(typedPrefix(parameters));
		PrefixMatcher matcher = results.getPrefixMatcher();
		InsertHandler<LookupElement> insertHandler =
			moduleDefinition != null ? new InterfaceImportInsertHandler(moduleDefinition) : null;

		for (String key : StubIndex.getInstance().getAllKeys(NameIndex.KEY, project))
		{
			if (!matcher.prefixMatches(simpleName(key)) && !matcher.prefixMatches(key)) continue;

			for (C3PsiElement element : elementsByName(key, project))
			{
				if (!(element instanceof C3TypeFullyQualifiedNamePsiElement typeName)) continue;
				if (typeName.getTypeEnum() != C3TypeEnum.INTERFACE) continue;

				FullyQualifiedName fqName = typeName.getFqName();
				results.addElement(LookupElementBuilder
					.create(typeName, fqName.getName())
					.withLookupStrings(List.of(fqName.getFullName(), fqName.getName()))
					.withPsiElement(typeName)
					.withIcon(C3Icons.Nodes.INTERFACE)
					.withPresentableText(Objects.equals(fqName.getModule(), moduleName)
						? fqName.getName()
						: fqName.getFullName())
					.withTypeText(fqName.getModule() != null ? fqName.getModule().toString() : "")
					.withInsertHandler(insertHandler));
			}
		}
	}

	/** Stub-index lookup guarded against a stale/inconsistent index (which would otherwise throw
	 *  and abort the whole completion session — i.e. no popup at all). */
	private static @NotNull java.util.Collection<C3PsiElement> elementsByName(
		@NotNull String key, @NotNull com.intellij.openapi.project.Project project)
	{
		try
		{
			return StubIndex.getElements(
				NameIndex.KEY, key, project, GlobalSearchScope.allScope(project), C3PsiElement.class);
		}
		catch (com.intellij.openapi.progress.ProcessCanceledException e)
		{
			throw e;
		}
		catch (Throwable t)
		{
			return List.of();
		}
	}

	/** The identifier text typed up to the caret (excluding the platform's inserted dummy). */
	private static @NotNull String typedPrefix(@NotNull CompletionParameters parameters)
	{
		PsiElement position = parameters.getPosition();
		String text = position.getText();
		int end = parameters.getOffset() - position.getTextRange().getStartOffset();
		end = Math.max(0, Math.min(end, text.length()));
		String prefix = text.substring(0, end);
		// Keep only the trailing identifier characters, dropping any leading punctuation.
		int start = prefix.length();
		while (start > 0 && (Character.isLetterOrDigit(prefix.charAt(start - 1)) || prefix.charAt(start - 1) == '_'))
		{
			start--;
		}
		return prefix.substring(start);
	}

	/** True when the caret sits inside the {@code (...)} interface list of a type declaration. */
	private static boolean inInterfaceImplContext(@Nullable PsiElement position)
	{
		PsiElement leaf = position != null ? PsiTreeUtil.prevLeaf(position) : null;
		while (leaf != null)
		{
			IElementType type = PsiUtilCore.getElementType(leaf);
			// Crossing a closing paren / brace / statement end means we are not in the list.
			if (type == C3Types.RP || type == C3Types.LB || type == C3Types.RB || type == C3Types.EOS)
			{
				return false;
			}
			if (type == C3Types.LP)
			{
				PsiElement name = prevSignificant(leaf);
				IElementType nameType = PsiUtilCore.getElementType(name);
				// The name before `(` is a TYPE_IDENT leaf (parsed) or a TYPE_NAME node (error state).
				if (nameType != C3Types.TYPE_IDENT && nameType != C3Types.TYPE_NAME) return false;
				return isTypeDeclarationKeyword(PsiUtilCore.getElementType(prevSignificant(name)));
			}
			leaf = PsiTreeUtil.prevLeaf(leaf);
		}
		return false;
	}

	private static boolean isTypeDeclarationKeyword(@Nullable IElementType type)
	{
		return type == C3Types.KW_STRUCT
			|| type == C3Types.KW_UNION
			|| type == C3Types.KW_BITSTRUCT
			|| type == C3Types.KW_TYPEDEF
			|| type == C3Types.KW_CONSTDEF
			|| type == C3Types.KW_ENUM;
	}

	private static @Nullable PsiElement prevSignificant(@Nullable PsiElement leaf)
	{
		PsiElement prev = leaf != null ? PsiTreeUtil.prevLeaf(leaf) : null;
		// Skip whitespace, comments and the zero-length error elements the parser inserts while the
		// declaration is still incomplete (e.g. the empty ERROR_ELEMENT it puts before `(`).
		while (prev instanceof PsiWhiteSpace || prev instanceof PsiComment
			|| prev instanceof PsiErrorElement || (prev != null && prev.getTextLength() == 0))
		{
			prev = PsiTreeUtil.prevLeaf(prev);
		}
		return prev;
	}

	private static @NotNull String simpleName(@NotNull String fqName)
	{
		int idx = fqName.lastIndexOf("::");
		return idx >= 0 ? fqName.substring(idx + 2) : fqName;
	}

	/** Inserts the interface's simple name (handled by the platform) and adds the import if needed. */
	private static final class InterfaceImportInsertHandler implements InsertHandler<LookupElement>
	{
		private final C3ModuleDefinition moduleDefinition;

		private InterfaceImportInsertHandler(@NotNull C3ModuleDefinition moduleDefinition)
		{
			this.moduleDefinition = moduleDefinition;
		}

		@Override
		public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item)
		{
			if (!(item.getPsiElement() instanceof C3TypeName element)) return;
			if (moduleDefinition.isSameModule(element) || element.getModuleName() == null) return;

			WriteCommandAction.runWriteCommandAction(context.getProject(), () -> {
				AddImportQuickFix.ImportAction importAction =
					AddImportQuickFix.addImportAsText(element, moduleDefinition);
				if (importAction != null) importAction.write(context.getDocument());
			});
		}
	}
}
