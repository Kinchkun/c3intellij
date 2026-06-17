package org.c3lang.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.c3lang.intellij.psi.C3CompoundStatement;
import org.c3lang.intellij.psi.C3FuncDefinition;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3LocalDeclarationStmt;
import org.c3lang.intellij.psi.C3MacroDefinition;
import org.c3lang.intellij.psi.C3ParamDecl;
import org.c3lang.intellij.psi.C3ParamPathElement;
import org.c3lang.intellij.psi.C3Parameter;
import org.c3lang.intellij.psi.C3ParameterList;
import org.c3lang.intellij.psi.C3PathIdentExpr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.and;

/** Completes in-scope local variables and function/macro parameters. */
public final class LocalCompletionContributor extends CompletionProvider<CompletionParameters>
{
	public static final LocalCompletionContributor INSTANCE = new LocalCompletionContributor();

	private static final ElementPattern<PsiElement> PATTERN = and(
		psiElement().inside(C3PathIdentExpr.class),
		psiElement().andNot(psiElement().inside(C3ParamPathElement.class))
	);

	private LocalCompletionContributor() {}

	@Override
	protected void addCompletions(
		@NotNull CompletionParameters parameters,
		@NotNull ProcessingContext context,
		@NotNull CompletionResultSet result)
	{
		if (!PATTERN.accepts(parameters.getPosition()) && !PATTERN.accepts(parameters.getOriginalPosition()))
		{
			return;
		}

		PsiElement position = parameters.getPosition();
		int offset = position.getTextRange().getStartOffset();
		Set<String> added = new HashSet<>();

		C3FuncDefinition func = PsiTreeUtil.getParentOfType(position, C3FuncDefinition.class);
		if (func != null && func.getFuncDef().getFnParameterList() != null)
		{
			addParameters(func.getFuncDef().getFnParameterList().getParameterList(), result, added);
		}

		C3MacroDefinition macro = PsiTreeUtil.getParentOfType(position, C3MacroDefinition.class);
		if (macro != null && macro.getMacroParams() != null)
		{
			addParameters(macro.getMacroParams().getParameterList(), result, added);
		}

		C3CompoundStatement compound = PsiTreeUtil.getParentOfType(position, C3CompoundStatement.class);
		if (compound != null)
		{
			for (C3LocalDeclAfterType decl : PsiTreeUtil.collectElementsOfType(compound, C3LocalDeclAfterType.class))
			{
				if (decl.getTextRange().getStartOffset() >= offset) continue;
				String name = decl.getName();
				if (name == null || !added.add(name)) continue;
				result.addElement(prioritized(LookupElementBuilder.create(name)
					.withIcon(AllIcons.Nodes.Variable)
					.withTypeText(localType(decl))));
			}
		}
	}

	private static void addParameters(
		@Nullable C3ParameterList parameterList, @NotNull CompletionResultSet result, @NotNull Set<String> added)
	{
		if (parameterList == null) return;
		for (C3ParamDecl decl : parameterList.getParamDeclList())
		{
			C3Parameter parameter = decl.getParameter();
			String name = parameter.getName();
			if (name == null || !added.add(name)) continue;
			result.addElement(prioritized(LookupElementBuilder.create(name)
				.withIcon(AllIcons.Nodes.Parameter)
				.withTypeText(parameter.getType() != null ? parameter.getType().getText() : "")));
		}
	}

	/** In-scope variables/parameters should rank above functions and types. */
	private static LookupElement prioritized(@NotNull LookupElement element)
	{
		return PrioritizedLookupElement.withPriority(element, 100000.0);
	}

	private static @NotNull String localType(@NotNull C3LocalDeclAfterType decl)
	{
		C3LocalDeclarationStmt stmt = PsiTreeUtil.getParentOfType(decl, C3LocalDeclarationStmt.class);
		if (stmt != null && stmt.getOptionalType() != null && stmt.getOptionalType().getType() != null)
		{
			return stmt.getOptionalType().getType().getText();
		}
		return "";
	}
}
