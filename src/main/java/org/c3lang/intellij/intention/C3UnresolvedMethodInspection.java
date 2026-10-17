package org.c3lang.intellij.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.c3lang.intellij.index.StructService;
import org.c3lang.intellij.psi.C3AccessIdent;
import org.c3lang.intellij.psi.C3File;
import org.c3lang.intellij.psi.C3Types;
import org.c3lang.intellij.psi.C3Visitor;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.jetbrains.annotations.NotNull;

/**
 * Flags a member access {@code receiver.member} (a method call or field access) whose {@code member}
 * does not exist on the receiver's type — e.g. {@code object.this_method_does_not_exist()} where
 * {@code object} is a {@code struct} that has no such method or field.
 *
 * <p>This inspection is deliberately <strong>conservative</strong>: marking valid code red is worse
 * than missing a typo, so it only reports when it is certain. It reports only when <em>all</em> of:
 * <ul>
 *   <li>the member name is a plain identifier — not {@code .@macro}, {@code .#hash}, {@code .typeid}
 *       or {@code .$eval}, which are special forms the index does not model;</li>
 *   <li>the immediate receiver's type is known ({@link C3AccessIdent#findReceiverType()} is non-null) —
 *       so we never guess about a receiver whose type we couldn't determine;</li>
 *   <li>that type is a <em>modeled struct/union</em>, i.e. it has at least one indexed field. This
 *       naturally excludes slices, arrays, enums and other built-in types whose members
 *       ({@code .len}, {@code .ptr}, {@code .ordinal}, …) are not in the index and would otherwise be
 *       false positives;</li>
 *   <li>the access resolves to nothing (no field and no method of that name).</li>
 * </ul>
 *
 * <p>Known limitations (intentional, to stay false-positive-free): a method/field on a struct that
 * declares no fields is not checked, and members reached through a call result ({@code foo().bar}) or
 * a {@code Type::ident} scope access are not checked. These can be tightened later.
 *
 * @see org.c3lang.intellij.psi.impl.C3AccessIdentMixinImpl the resolution this inspection enforces
 */
public final class C3UnresolvedMethodInspection extends LocalInspectionTool
{
	@Override
	public @NotNull String getDisplayName()
	{
		return "Unresolved method or field";
	}

	@Override
	public @NotNull String getGroupDisplayName()
	{
		return "C3";
	}

	@Override
	public @NotNull PsiElementVisitor buildVisitor(
		@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session)
	{
		if (!(session.getFile() instanceof C3File)) return PsiElementVisitor.EMPTY_VISITOR;

		Project project = holder.getProject();
		return new C3Visitor()
		{
			@Override
			public void visitAccessIdent(@NotNull C3AccessIdent access)
			{
				// Only plain `.identifier` accesses. Skip .@macro / .#hash / .typeid / .$eval(...),
				// whose leaf token is not IDENT and which the struct/name index does not cover.
				LeafPsiElement nameElement = access.getNameIdentElement();
				if (nameElement == null || nameElement.getElementType() != C3Types.IDENT) return;

				// Only proceed when we positively know the receiver's type.
				FullyQualifiedName receiverType = access.findReceiverType();
				if (receiverType == null) return;

				// Only check types we actually model: a struct/union with at least one indexed field.
				// Slices/arrays/enums/built-ins have none, so their built-in members are never flagged.
				if (StructService.INSTANCE.findStructMemberFields(receiverType.getFullName(), project).isEmpty())
				{
					return;
				}

				// If it resolves to a field or a method, it's valid — nothing to report.
				if (access.getReference().resolve() != null) return;

				holder.registerProblem(
					nameElement,
					"Cannot resolve '" + access.getNameIdent() + "' on type '" + receiverType.getName() + "'",
					ProblemHighlightType.GENERIC_ERROR);
			}
		};
	}
}
