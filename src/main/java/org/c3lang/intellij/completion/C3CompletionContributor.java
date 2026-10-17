package org.c3lang.intellij.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionType;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * The single C3 completion entry point – it wires together the many specialised completion providers.
 *
 * <p>A {@link CompletionContributor} is what populates the popup when the user presses Ctrl-Space.
 * Rather than one giant provider, C3 splits completion by context into focused
 * {@code *CompletionContributor} providers (enum access, locals, functions, types, imports...). Each
 * is registered here with {@link #extend}; the platform asks them in turn and merges their results.
 * Order matters because some providers call {@code result.stopHere()} to suppress later ones when
 * they own the context (e.g. after {@code EnumType.}).
 *
 * <p>Registered in {@code plugin.xml} via {@code <completion.contributor language="C3" .../>}. To add
 * a new kind of suggestion, write another provider and add one {@code extend(...)} line below.
 */
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
		// At the caret the text is usually incomplete and would not parse. The platform inserts a
		// throwaway "dummy identifier" there so the file still parses and we get a usable PSI tree to
		// reason about; we override it with a C3-friendly placeholder.
		context.setDummyIdentifier(CompletionExtensionsKt.DUMMY_IDENTIFIER);
	}
}
