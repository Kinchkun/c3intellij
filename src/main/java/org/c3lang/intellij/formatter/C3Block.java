package org.c3lang.intellij.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.SpacingBuilder;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import org.c3lang.intellij.psi.C3Types;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Formatting block driving Reformat Code and on-Enter indentation.
 *
 * <p>Indentation rules:
 * <ul>
 *   <li>{@code { ... }} blocks indent their contents and <em>stack</em> per nesting level.</li>
 *   <li>{@code ( ... )} / {@code [ ... ]} indent wrapped contents by a single continuation
 *       level that does <em>not</em> stack across nested parentheses (so a multi-line call
 *       inside several parens still lines up at one level).</li>
 * </ul>
 */
public final class C3Block extends AbstractBlock
{
	private final Indent indent;
	private final boolean insideContinuation;
	private final SpacingBuilder spacingBuilder;

	C3Block(
		@NotNull ASTNode node,
		@Nullable Wrap wrap,
		@Nullable Alignment alignment,
		@NotNull Indent indent,
		boolean insideContinuation,
		@NotNull SpacingBuilder spacingBuilder)
	{
		super(node, wrap, alignment);
		this.indent = indent;
		this.insideContinuation = insideContinuation;
		this.spacingBuilder = spacingBuilder;
	}

	@Override
	protected List<Block> buildChildren()
	{
		// Only children positioned *between* an opening and closing bracket are indented.
		// Tracking the bracket depth (rather than "any child of a brace-containing node")
		// keeps header tokens that precede the brace at the declaration's own level, e.g.
		// the `constdef`/`enum` keywords where `{` is a direct child.
		List<Block> blocks = new ArrayList<>();
		int curlyDepth = 0;
		int parenDepth = 0;

		for (ASTNode child = myNode.getFirstChildNode(); child != null; child = child.getTreeNext())
		{
			if (child.getElementType() == TokenType.WHITE_SPACE || child.getTextLength() == 0) continue;

			IElementType type = child.getElementType();

			boolean closesCurly = type == C3Types.RB;
			boolean closesParen = type == C3Types.RP || type == C3Types.RBT || type == C3Types.RVEC;
			if (closesCurly && curlyDepth > 0) curlyDepth--;
			if (closesParen && parenDepth > 0) parenDepth--;

			Indent blockIndent;
			boolean childContinuation = insideContinuation;
			if (isBracket(type))
			{
				// The brackets themselves stay at the enclosing level.
				blockIndent = Indent.getNoneIndent();
			}
			else if (curlyDepth > 0)
			{
				// Inside { }: indent (stacks across nested braces via recursion).
				blockIndent = Indent.getNormalIndent();
				childContinuation = false;
			}
			else if (parenDepth > 0)
			{
				// Inside ( ) / [ ]: a single continuation level that does not stack.
				blockIndent = insideContinuation ? Indent.getNoneIndent() : Indent.getNormalIndent();
				childContinuation = true;
			}
			else
			{
				blockIndent = Indent.getNoneIndent();
			}

			blocks.add(new C3Block(child, null, null, blockIndent, childContinuation, spacingBuilder));

			if (type == C3Types.LB) curlyDepth++;
			if (type == C3Types.LP || type == C3Types.LBT || type == C3Types.LVEC) parenDepth++;
		}
		return blocks;
	}

	@Override
	public Indent getIndent()
	{
		return indent;
	}

	@Override
	public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2)
	{
		return spacingBuilder.getSpacing(this, child1, child2);
	}

	@Override
	public boolean isLeaf()
	{
		return myNode.getFirstChildNode() == null;
	}

	@Override
	public @NotNull ChildAttributes getChildAttributes(int newChildIndex)
	{
		boolean curly = hasChild(myNode, C3Types.LB);
		boolean bracketed = hasChild(myNode, C3Types.LP)
			|| hasChild(myNode, C3Types.LBT)
			|| hasChild(myNode, C3Types.LVEC);

		Indent childIndent = (curly || (bracketed && !insideContinuation))
			? Indent.getNormalIndent()
			: Indent.getNoneIndent();
		return new ChildAttributes(childIndent, null);
	}

	private static boolean isBracket(@NotNull IElementType type)
	{
		return type == C3Types.LB || type == C3Types.RB
			|| type == C3Types.LP || type == C3Types.RP
			|| type == C3Types.LBT || type == C3Types.RBT
			|| type == C3Types.LVEC || type == C3Types.RVEC;
	}

	private static boolean hasChild(@NotNull ASTNode node, @NotNull IElementType type)
	{
		for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext())
		{
			if (child.getElementType() == type) return true;
		}
		return false;
	}
}
