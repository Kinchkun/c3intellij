package org.c3lang.intellij.intention;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiTreeUtil;
import org.c3lang.intellij.C3SourceFileType;
import org.c3lang.intellij.index.TypeIndex;
import org.c3lang.intellij.psi.C3AccessIdent;
import org.c3lang.intellij.psi.C3Arg;
import org.c3lang.intellij.psi.C3ArgList;
import org.c3lang.intellij.psi.C3CallExpr;
import org.c3lang.intellij.psi.C3CallExprTail;
import org.c3lang.intellij.psi.C3Expr;
import org.c3lang.intellij.psi.C3ExprStmt;
import org.c3lang.intellij.psi.C3FuncDefinition;
import org.c3lang.intellij.psi.C3GlobalDecl;
import org.c3lang.intellij.psi.C3GlobalName;
import org.c3lang.intellij.psi.C3KeywordExpr;
import org.c3lang.intellij.psi.C3LiteralExpr;
import org.c3lang.intellij.psi.C3LocalDeclAfterType;
import org.c3lang.intellij.psi.C3OptionalType;
import org.c3lang.intellij.psi.C3Parameter;
import org.c3lang.intellij.psi.C3PathIdent;
import org.c3lang.intellij.psi.C3PathIdentExpr;
import org.c3lang.intellij.psi.C3PsiElement;
import org.c3lang.intellij.psi.C3ReturnStmt;
import org.c3lang.intellij.psi.C3StringExpr;
import org.c3lang.intellij.psi.C3TypeName;
import org.c3lang.intellij.psi.C3Types;
import org.c3lang.intellij.psi.C3UnaryExpr;
import org.c3lang.intellij.psi.FullyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates the method an unresolved call names: for {@code req.free(mem);} on a {@code HttpRequest}
 * it adds {@code fn void HttpRequest.free(&self, Allocator allocator)} to the file that declares
 * the struct, after the struct's last method.
 *
 * <p>The parameters come from the arguments: a variable gives its name and declared type, a
 * literal its type; an argument whose type is not known becomes {@code any}, to be corrected by
 * hand. The return type comes from where the call stands: the declared type of the local it
 * initializes, the return type of the function it is returned from, {@code void} for a statement;
 * a {@code !} or {@code !!} behind the call makes it optional.</p>
 */
public final class CreateMethodFix implements LocalQuickFix
{
	private static final String UNKNOWN_TYPE = "any";

	private final String typeFullName;
	private final String typeName;
	private final String methodName;

	private CreateMethodFix(@NotNull FullyQualifiedName type, @NotNull String methodName)
	{
		this.typeFullName = type.getFullName();
		this.typeName = type.getName();
		this.methodName = methodName;
	}

	/**
	 * The fix for the unresolved member {@code access}, or null when it is not called
	 * ({@code value.field}) or when the type is declared where it cannot be changed (a library).
	 */
	public static @Nullable CreateMethodFix forAccess(@NotNull C3AccessIdent access, @NotNull FullyQualifiedName receiverType)
	{
		String name = access.getNameIdent();
		if (name == null || invocationOf(access) == null) return null;
		C3TypeName declaration = declarationOf(receiverType.getFullName(), access.getProject());
		if (declaration == null) return null;
		VirtualFile file = declaration.getContainingFile().getVirtualFile();
		if (file == null || !file.isWritable() || ProjectFileIndex.getInstance(access.getProject()).isInLibrary(file)) return null;
		return new CreateMethodFix(receiverType, name);
	}

	@Override
	public @NotNull String getName()
	{
		return "Create method '" + typeName + "." + methodName + "'";
	}

	@Override
	public @NotNull String getFamilyName()
	{
		return "Create method";
	}

	@Override
	public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile)
	{
		C3TypeName declaration = declarationOf(typeFullName, currentFile.getProject());
		return declaration != null ? declaration.getContainingFile() : currentFile;
	}

	/** The method is written into the struct's file, so the preview shows its text and changes nothing. */
	@Override
	public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor)
	{
		C3AccessIdent access = PsiTreeUtil.getParentOfType(previewDescriptor.getPsiElement(), C3AccessIdent.class, false);
		if (access == null) return IntentionPreviewInfo.EMPTY;
		return new IntentionPreviewInfo.CustomDiff(C3SourceFileType.INSTANCE, "", methodText(access, false));
	}

	@Override
	public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
	{
		C3AccessIdent access = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), C3AccessIdent.class, false);
		C3TypeName declaration = declarationOf(typeFullName, project);
		if (access == null || declaration == null) return;

		PsiFile file = declaration.getContainingFile();
		Document document = PsiDocumentManager.getInstance(project).getDocument(file);
		if (document == null) return;

		String method = methodText(access, bracesOnOwnLine(file));
		int offset = insertionOffset(declaration, file);
		document.insertString(offset, "\n\n" + method);
		PsiDocumentManager.getInstance(project).commitDocument(document);

		// Put the caret into the empty body.
		VirtualFile virtualFile = file.getVirtualFile();
		int body = offset + 2 + method.lastIndexOf('}');
		if (virtualFile != null && !ApplicationManager.getApplication().isUnitTestMode())
		{
			ApplicationManager.getApplication().invokeLater(() ->
				new OpenFileDescriptor(project, virtualFile, body).navigate(true), project.getDisposed());
		}
	}

	/** {@code fn void HttpRequest.free(&self, Allocator allocator)} with an empty body. */
	private @NotNull String methodText(@NotNull C3AccessIdent access, boolean bracesOnOwnLine)
	{
		C3CallExpr invocation = invocationOf(access);
		StringBuilder text = new StringBuilder("fn ")
			.append(invocation != null ? returnType(invocation) : "void")
			.append(' ').append(typeName).append('.').append(methodName).append("(&self");
		if (invocation != null)
		{
			for (String parameter : parameters(invocation)) text.append(", ").append(parameter);
		}
		return text.append(bracesOnOwnLine ? ")\n{\n}" : ") {\n}").toString();
	}

	/** The call {@code receiver.name(...)} whose callee is {@code access}, or null for a plain member access. */
	private static @Nullable C3CallExpr invocationOf(@NotNull C3AccessIdent access)
	{
		C3CallExpr member = PsiTreeUtil.getParentOfType(access, C3CallExpr.class);
		if (member == null || !(member.getParent() instanceof C3CallExpr call) || call.getExpr() != member) return null;
		C3CallExprTail tail = call.getCallExprTail();
		return tail != null && tail.getCallInvocation() != null ? call : null;
	}

	private static @Nullable C3TypeName declarationOf(@NotNull String typeFullName, @NotNull Project project)
	{
		for (C3PsiElement element : StubIndex.getElements(
			TypeIndex.KEY, typeFullName, project, GlobalSearchScope.allScope(project), C3PsiElement.class))
		{
			if (element instanceof C3TypeName typeName) return typeName;
		}
		return null;
	}

	/** After the type's last method in its file, else after the declaration of the type. */
	private int insertionOffset(@NotNull C3TypeName declaration, @NotNull PsiFile file)
	{
		PsiElement last = declaration.getNode().getTreeParent().getPsi();
		String prefix = typeName + ".";
		for (C3FuncDefinition function : PsiTreeUtil.findChildrenOfType(file, C3FuncDefinition.class))
		{
			if (function.getFuncDef().getFqName().getName().startsWith(prefix)
				&& function.getTextRange().getEndOffset() > last.getTextRange().getEndOffset())
			{
				last = function;
			}
		}
		return last.getTextRange().getEndOffset();
	}

	/** Whether the functions of {@code file} open their body on a line of its own; the new method follows suit. */
	private static boolean bracesOnOwnLine(@NotNull PsiFile file)
	{
		for (C3FuncDefinition function : PsiTreeUtil.findChildrenOfType(file, C3FuncDefinition.class))
		{
			String text = function.getText();
			int brace = text.indexOf('{');
			int close = brace > 0 ? text.lastIndexOf(')', brace) : -1;
			if (close < 0) continue;
			return text.substring(close, brace).contains("\n");
		}
		return true;
	}

	/** {@code Type name} for each argument of the call. */
	private static @NotNull List<String> parameters(@NotNull C3CallExpr invocation)
	{
		List<String> parameters = new ArrayList<>();
		C3ArgList arguments = invocation.getCallExprTail().getCallInvocation().getCallArgList().getArgList();
		if (arguments == null) return parameters;

		Set<String> used = new HashSet<>(Set.of("self"));
		int position = 0;
		for (C3Arg argument : arguments.getArgList())
		{
			position++;
			C3Expr value = argument.getExpr();
			String type = value != null ? typeOf(value) : null;
			String name = argument.getNamedIdent() != null ? argument.getNamedIdent().getText() : nameFor(value, type, position);
			while (!used.add(name)) name = name + position;
			parameters.add((type != null ? type : UNKNOWN_TYPE) + " " + name);
		}
		return parameters;
	}

	private static @NotNull String nameFor(@Nullable C3Expr value, @Nullable String type, int position)
	{
		if ("Allocator".equals(type)) return "allocator";
		if (value instanceof C3PathIdentExpr variable)
		{
			String name = variable.getPathIdent().getNameIdent();
			if (name != null) return name;
		}
		return "param" + position;
	}

	/** The type of an argument as far as it can be read off: a literal, a variable's declared type, {@code &variable}. */
	private static @Nullable String typeOf(@NotNull C3Expr value)
	{
		if (value instanceof C3StringExpr) return value.getText().startsWith("'") ? "char" : "String";
		if (value instanceof C3LiteralExpr literal)
		{
			return literal.getNode().findChildByType(C3Types.FLOAT_LITERAL) != null ? "double" : "int";
		}
		if (value instanceof C3KeywordExpr keyword) return keyword.getText().equals("null") ? "void*" : "bool";
		if (value instanceof C3UnaryExpr unary && unary.getUnaryOp().getText().equals("&") && unary.getExpr() != null)
		{
			String pointee = typeOf(unary.getExpr());
			return pointee != null ? pointee + "*" : null;
		}
		if (value instanceof C3PathIdentExpr variable) return declaredTypeOf(variable.getPathIdent());
		if (value instanceof C3CallExpr call)
		{
			C3OptionalType returned = C3Callees.returnType(C3Callees.resolve(call));
			return returned != null ? returned.getType().getText() : null;
		}
		return null;
	}

	private static @Nullable String declaredTypeOf(@NotNull C3PathIdent variable)
	{
		// The heap and temp allocators are aliases of thread-local variables.
		String name = variable.getNameIdent();
		if ("mem".equals(name) || "tmem".equals(name)) return "Allocator";

		PsiReference reference = variable.getReference();
		PsiElement declaration = reference != null ? reference.resolve() : null;
		if (declaration instanceof C3LocalDeclAfterType local && local.getDeclaredType() != null) return typeText(local.getDeclaredType());
		if (declaration instanceof C3Parameter parameter && parameter.getType() != null) return parameter.getType().getText();
		if (declaration instanceof C3GlobalName global && global.getParent() instanceof C3GlobalDecl globalDecl) return typeText(globalDecl.getOptionalType());
		FullyQualifiedName type = variable.findTypeName();
		return type != null ? type.getName() : null;
	}

	/** The type without the {@code ?} of an optional variable: a parameter is never optional. */
	private static @NotNull String typeText(@NotNull C3OptionalType type)
	{
		return type.getType().getText();
	}

	/** What the place of the call expects: see the class comment. */
	private static @NotNull String returnType(@NotNull C3CallExpr invocation)
	{
		PsiElement expression = invocation;
		boolean optional = false;
		// `call()!` and `call()!!` are call expressions around the call.
		while (expression.getParent() instanceof C3CallExpr unwrap && unwrap.getExpr() == expression && isUnwrap(unwrap))
		{
			optional = true;
			expression = unwrap;
		}

		String type = "void";
		PsiElement context = expression.getParent();
		if (context instanceof C3LocalDeclAfterType local && local.getDeclaredType() != null)
		{
			type = typeText(local.getDeclaredType());
			optional |= C3OptionalUnwrapInspection.isOptional(local.getDeclaredType());
		}
		else if (context instanceof C3ReturnStmt)
		{
			C3OptionalType returned = C3OptionalUnwrapInspection.enclosingReturnType(context);
			if (returned != null)
			{
				type = typeText(returned);
				optional |= C3OptionalUnwrapInspection.isOptional(returned);
			}
		}
		else if (!(context instanceof C3ExprStmt))
		{
			return "void";
		}
		return optional ? type + "?" : type;
	}

	private static boolean isUnwrap(@NotNull C3CallExpr call)
	{
		C3CallExprTail tail = call.getCallExprTail();
		return tail != null
			&& (tail.getNode().findChildByType(C3Types.BANG) != null || tail.getNode().findChildByType(C3Types.BANGBANG) != null);
	}
}
