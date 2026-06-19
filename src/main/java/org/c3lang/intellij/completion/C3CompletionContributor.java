package org.c3lang.intellij.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionType;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public final class C3CompletionContributor extends CompletionContributor
{
	public C3CompletionContributor()
	{
		var pattern = psiElement();

		// First: after `EnumType.` only the enum's constants are valid, and this provider stops the
		// chain so the broken-parse position doesn't also draw type/value completions.
		extend(CompletionType.BASIC, pattern, EnumAccessCompletionContributor.INSTANCE);
		extend(CompletionType.BASIC, pattern, LocalCompletionContributor.INSTANCE);
		extend(CompletionType.BASIC, pattern, FunctionCompletionContributor.INSTANCE);
		extend(CompletionType.BASIC, pattern, NamedArgumentCompletionContributor.INSTANCE);
		extend(CompletionType.BASIC, pattern, InterfaceCompletionContributor.INSTANCE);
		extend(CompletionType.BASIC, pattern, TypeCompletionContributor.INSTANCE);
		extend(CompletionType.BASIC, pattern, ImportCompletionContributor.INSTANCE);
		extend(CompletionType.BASIC, pattern, ConstCompletionContributor.INSTANCE);
		extend(CompletionType.BASIC, pattern, FaultCompletionContributor.INSTANCE);
		extend(CompletionType.BASIC, pattern, TailExprCompletionContributor.INSTANCE);
		extend(CompletionType.BASIC, pattern, InitializerListCompletionContributor.INSTANCE);
		extend(CompletionType.BASIC, pattern, DocCommentCompletionContributor.INSTANCE);
		extend(CompletionType.BASIC, pattern, TopLevelCompletionContributor.INSTANCE);
	}

	@Override
	public void beforeCompletion(CompletionInitializationContext context)
	{
		// path
		context.setDummyIdentifier(CompletionExtensionsKt.DUMMY_IDENTIFIER);
	}
}
