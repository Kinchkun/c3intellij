package org.c3lang.intellij.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.formatting.Indent;
import com.intellij.formatting.SpacingBuilder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.c3lang.intellij.C3Language;
import org.c3lang.intellij.psi.C3Types;
import org.jetbrains.annotations.NotNull;

public final class C3FormattingModelBuilder implements FormattingModelBuilder
{
	@Override
	public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext)
	{
		PsiFile file = formattingContext.getContainingFile();
		CodeStyleSettings settings = formattingContext.getCodeStyleSettings();
		SpacingBuilder spacingBuilder = createSpacingBuilder(settings);

		Block rootBlock = new C3Block(file.getNode(), null, null, Indent.getNoneIndent(), false, spacingBuilder);
		return FormattingModelProvider.createFormattingModelForPsiFile(file, rootBlock, settings);
	}

	private static SpacingBuilder createSpacingBuilder(@NotNull CodeStyleSettings settings)
	{
		return new SpacingBuilder(settings, C3Language.INSTANCE)
			// `,` hugs the preceding token and is followed by a single space (line breaks kept).
			.before(C3Types.COMMA).spaces(0)
			.after(C3Types.COMMA).spaces(1)
			// No space before the statement terminator.
			.before(C3Types.EOS).spaces(0)
			// No padding just inside parentheses: (true), foo(a).
			.after(C3Types.LP).spaces(0)
			.before(C3Types.RP).spaces(0)
			// Control keywords hug their parenthesis: while(...), if(...), for(...).
			.between(C3Types.KW_WHILE, C3Types.PAREN_COND).spaces(0)
			.between(C3Types.KW_IF, C3Types.PAREN_COND).spaces(0)
			.between(C3Types.KW_SWITCH, C3Types.PAREN_COND).spaces(0)
			.between(C3Types.KW_FOR, C3Types.LP).spaces(0)
			.between(C3Types.KW_FOREACH, C3Types.LP).spaces(0)
			.between(C3Types.KW_FOREACH_R, C3Types.LP).spaces(0);
	}
}
