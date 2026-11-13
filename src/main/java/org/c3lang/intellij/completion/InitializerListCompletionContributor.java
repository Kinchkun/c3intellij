package org.c3lang.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import kotlin.Pair;
import org.c3lang.intellij.C3Icons;
import org.c3lang.intellij.index.StructService;
import org.c3lang.intellij.intention.C3Callees;
import org.c3lang.intellij.psi.AccessPath;
import org.c3lang.intellij.psi.C3Arg;
import org.c3lang.intellij.psi.C3ArgList;
import org.c3lang.intellij.psi.C3BinaryExpr;
import org.c3lang.intellij.psi.C3CallExpr;
import org.c3lang.intellij.psi.C3CallExprTail;
import org.c3lang.intellij.psi.C3CallInvocation;
import org.c3lang.intellij.psi.C3CallablePsiElement;
import org.c3lang.intellij.psi.C3CompoundInitExpr;
import org.c3lang.intellij.psi.C3CompoundStatement;
import org.c3lang.intellij.psi.C3Expr;
import org.c3lang.intellij.psi.C3FuncDef;
import org.c3lang.intellij.psi.C3FuncDefinition;
import org.c3lang.intellij.psi.C3InitListExpr;
import org.c3lang.intellij.psi.C3InitializerList;
import org.c3lang.intellij.psi.C3LambdaDeclExpr;
import org.c3lang.intellij.psi.C3LambdaDeclShortExpr;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3MacroDefinition;
import org.c3lang.intellij.psi.C3OptionalType;
import org.c3lang.intellij.psi.C3ParamDecl;
import org.c3lang.intellij.psi.C3ParameterList;
import org.c3lang.intellij.psi.C3PathIdent;
import org.c3lang.intellij.psi.C3PathIdentExpr;
import org.c3lang.intellij.psi.C3ReturnStmt;
import org.c3lang.intellij.psi.C3StructMemberDeclaration;
import org.c3lang.intellij.psi.C3Type;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Completes the field after the {@code .} of a designated initializer:
 * {@code return { .status = 200, .<caret> }} offers the remaining fields of the returned struct,
 * in declaration order, and inserts {@code name = }.
 *
 * <p>The struct is the type the initializer list is written for: the declared type of the local
 * ({@code Response r = { . }}), the target of an assignment, the return type of the enclosing
 * function, macro or lambda, the type of a compound literal ({@code (Response){ . }}), the type of
 * the parameter the list is passed for, or, for a nested list ({@code .header = { . }}), the type
 * of that field.</p>
 *
 * <p>The position is a closed one: nothing but a field name is valid, so the provider ends the
 * chain when it knows the fields.</p>
 */
public final class InitializerListCompletionContributor extends CompletionProvider<CompletionParameters>
{
	public static final InitializerListCompletionContributor INSTANCE = new InitializerListCompletionContributor();

	private static final Key<Integer> DECLARATION_OFFSET = Key.create("c3.struct.field.offset");

	private static final CompletionSorter DECLARATION_ORDER = CompletionSorter.emptySorter()
		.weigh(new LookupElementWeigher("c3FieldDeclarationOrder")
		{
			@Override
			public @NotNull Comparable<?> weigh(@NotNull LookupElement element)
			{
				Integer offset = element.getUserData(DECLARATION_OFFSET);
				return offset != null ? offset : Integer.MAX_VALUE;
			}
		});

	private InitializerListCompletionContributor()
	{
	}

	@Override
	protected void addCompletions(
		@NotNull CompletionParameters parameters,
		@NotNull ProcessingContext context,
		@NotNull CompletionResultSet result)
	{
		PsiFile file = parameters.getOriginalFile();
		CharSequence text = file.getViewProvider().getContents();
		int dot = designatorDot(text, Math.min(parameters.getOffset(), text.length()));
		if (dot < 0) return;

		C3InitializerList list = enclosingInitializerList(file.findElementAt(dot));
		if (list == null) return;
		FullyQualifiedName type = initializedType(list);
		if (type == null) return;

		Project project = file.getProject();
		Set<String> alreadySet = designatedFields(list, dot);
		CompletionResultSet ordered = result.withRelevanceSorter(DECLARATION_ORDER);
		boolean added = false;
		for (Field field : fields(type, project))
		{
			if (alreadySet.contains(field.name)) continue;
			LookupElement element = LookupElementBuilder.create(field.name)
				.withIcon(C3Icons.Nodes.STRUCT_FIELD)
				.withTypeText(field.type)
				.withInsertHandler(InitializerListCompletionContributor::insertAssignment);
			element.putUserData(DECLARATION_OFFSET, field.offset);
			ordered.addElement(element);
			added = true;
		}
		if (added) result.stopHere();
	}

	private record Field(@NotNull String name, @NotNull String type, int offset) {}

	/** The fields of {@code type} itself, in declaration order. */
	private static @NotNull List<Field> fields(@NotNull FullyQualifiedName type, @NotNull Project project)
	{
		List<Field> fields = new ArrayList<>();
		for (Pair<AccessPath, String> field : StructService.INSTANCE.getFields(type, List.of(""), project))
		{
			AccessPath path = field.getFirst();
			if (path.getSegments().size() != 1) continue;
			List<C3StructMemberDeclaration> declarations = StructService.INSTANCE.getStructMembers(type, path.getName(), project);
			int offset = declarations.isEmpty() ? Integer.MAX_VALUE : declarations.get(0).getTextOffset();
			fields.add(new Field(path.getName(), field.getSecond(), offset));
		}
		fields.sort(Comparator.comparingInt(Field::offset));
		return fields;
	}

	/**
	 * The offset of the {@code .} that starts the designator being typed at {@code caret}, or -1
	 * when the caret is not in a designator: the dot must open an element of a list, so it follows
	 * {@code {} or {@code ,}. In {@code { .a = value.<caret> }} the dot is a member access.
	 */
	private static int designatorDot(@NotNull CharSequence text, int caret)
	{
		int index = caret;
		while (index > 0 && (Character.isLetterOrDigit(text.charAt(index - 1)) || text.charAt(index - 1) == '_')) index--;
		if (index == 0 || text.charAt(index - 1) != '.') return -1;
		int dot = index - 1;
		int before = dot;
		while (before > 0 && Character.isWhitespace(text.charAt(before - 1))) before--;
		if (before == 0) return -1;
		char opener = text.charAt(before - 1);
		return opener == '{' || opener == ',' ? dot : -1;
	}

	/** The initializer list {@code element} is directly in: not one around a call or a block that contains it. */
	private static @Nullable C3InitializerList enclosingInitializerList(@Nullable PsiElement element)
	{
		PsiElement scope = PsiTreeUtil.getParentOfType(element,
			C3InitializerList.class, C3CallInvocation.class, C3CompoundStatement.class);
		return scope instanceof C3InitializerList list ? list : null;
	}

	/** The names of the fields the list already sets, except the one being typed at {@code dot}. */
	private static @NotNull Set<String> designatedFields(@NotNull C3InitializerList list, int dot)
	{
		Set<String> names = new HashSet<>();
		C3ArgList arguments = list.getArgList();
		if (arguments == null) return names;
		for (C3Arg argument : arguments.getArgList())
		{
			if (argument.getParamPath() == null || argument.getTextRange().getStartOffset() == dot) continue;
			String name = fieldName(argument);
			if (name != null) names.add(name);
		}
		return names;
	}

	/** {@code header} for the designator {@code .header = ...} (or {@code .header.name = ...}). */
	private static @Nullable String fieldName(@NotNull C3Arg argument)
	{
		if (argument.getParamPath() == null) return null;
		String path = argument.getParamPath().getText();
		if (!path.startsWith(".")) return null;
		int end = 1;
		while (end < path.length() && (Character.isLetterOrDigit(path.charAt(end)) || path.charAt(end) == '_')) end++;
		return end > 1 ? path.substring(1, end) : null;
	}

	/** The struct type an initializer list is written for, or null when it is not known. */
	private static @Nullable FullyQualifiedName initializedType(@NotNull C3InitializerList list)
	{
		PsiElement owner = list.getParent();
		if (owner instanceof C3CompoundInitExpr compound) return typeName(compound.getType());
		if (!(owner instanceof C3InitListExpr expression)) return null;

		PsiElement context = expression.getParent();
		if (context instanceof C3LocalDeclAfterType local) return local.findTypeName();
		if (context instanceof C3ReturnStmt) return typeName(enclosingReturnType(context));
		if (context instanceof C3BinaryExpr assignment && assignment.getRight() == expression) return assignedType(assignment.getLeft());
		if (context instanceof C3Arg argument) return argumentType(argument);
		return null;
	}

	/** The type of the target of an assignment: a variable ({@code response}) or a member ({@code self.response}). */
	private static @Nullable FullyQualifiedName assignedType(@NotNull C3Expr target)
	{
		if (target instanceof C3PathIdentExpr variable) return variable.getPathIdent().findTypeName();
		if (target instanceof C3CallExpr access)
		{
			C3CallExprTail tail = access.getCallExprTail();
			return tail != null && tail.getAccessIdent() != null ? tail.getAccessIdent().findTypeName() : null;
		}
		return null;
	}

	/**
	 * The type an argument is written for: the field of the outer struct for a nested initializer
	 * ({@code .header = { . }}), else the parameter of the called function or macro.
	 */
	private static @Nullable FullyQualifiedName argumentType(@NotNull C3Arg argument)
	{
		if (!(argument.getParent() instanceof C3ArgList arguments)) return null;

		if (arguments.getParent() instanceof C3InitializerList outer)
		{
			String field = fieldName(argument);
			FullyQualifiedName outerType = initializedType(outer);
			if (field == null || outerType == null) return null;
			List<C3StructMemberDeclaration> members = StructService.INSTANCE.getStructMembers(outerType, field, argument.getProject());
			return members.size() == 1 ? members.get(0).findTypeName() : null;
		}

		C3CallExpr call = PsiTreeUtil.getParentOfType(arguments, C3CallExpr.class);
		if (call == null || argument.getNamedIdent() != null) return null;
		PsiElement callee = C3Callees.resolve(call);
		C3ParameterList parameters = callee instanceof C3FuncDef function ? function.getFnParameterList().getParameterList()
			: callee instanceof C3MacroDefinition macro ? macro.getMacroParams().getParameterList() : null;
		if (parameters == null) return null;

		// `server.start(...)`: the receiver is the first parameter and no argument.
		boolean method = callee instanceof C3CallablePsiElement callable && callable.getType() != null
			&& call.getExpr() instanceof C3CallExpr receiver
			&& receiver.getCallExprTail() != null && receiver.getCallExprTail().getAccessIdent() != null;
		int index = arguments.getArgList().indexOf(argument) + (method ? 1 : 0);
		List<C3ParamDecl> declarations = parameters.getParamDeclList();
		return index >= 0 && index < declarations.size() ? declarations.get(index).getParameter().findTypeName() : null;
	}

	/** The written return type of the function, macro or lambda around {@code element}. */
	private static @Nullable C3Type enclosingReturnType(@NotNull PsiElement element)
	{
		PsiElement callable = PsiTreeUtil.getParentOfType(element,
			C3FuncDefinition.class, C3MacroDefinition.class, C3LambdaDeclExpr.class, C3LambdaDeclShortExpr.class);
		C3OptionalType type = null;
		if (callable instanceof C3FuncDefinition function) type = function.getFuncDef().getFuncHeader().getOptionalType();
		if (callable instanceof C3MacroDefinition macro) type = macro.getMacroHeader().getOptionalType();
		if (callable instanceof C3LambdaDeclExpr lambda) type = lambda.getLambdaDecl().getOptionalType();
		if (callable instanceof C3LambdaDeclShortExpr lambda) type = lambda.getLambdaDecl().getOptionalType();
		return type != null ? type.getType() : null;
	}

	/** The name of a written type, in the module that declares it. */
	private static @Nullable FullyQualifiedName typeName(@Nullable C3Type type)
	{
		return type != null ? type.getModuleDefinition().resolveDeclaringModule(FullyQualifiedName.Companion.from(type)) : null;
	}

	/** Completes {@code .status} to {@code .status = }, unless the {@code =} is already there. */
	private static void insertAssignment(@NotNull InsertionContext context, @NotNull LookupElement item)
	{
		CharSequence text = context.getDocument().getCharsSequence();
		int tail = context.getTailOffset();
		int next = tail;
		while (next < text.length() && (text.charAt(next) == ' ' || text.charAt(next) == '\t')) next++;
		if (next < text.length() && text.charAt(next) == '=') return;
		context.getDocument().insertString(tail, " = ");
		context.getEditor().getCaretModel().moveToOffset(tail + 3);
	}
}
