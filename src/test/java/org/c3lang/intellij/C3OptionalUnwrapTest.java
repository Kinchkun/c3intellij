package org.c3lang.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import org.c3lang.intellij.intention.C3OptionalUnwrapInspection;

import java.util.List;

/**
 * An optional value (`usz?`) assigned to or returned as a plain `usz` is an error, as in the
 * compiler; the "Propagate the fault" fix adds `!` and makes the enclosing function return `usz?`.
 */
public class C3OptionalUnwrapTest extends C3TestCase
{
	private static final String HEAD = "module app;\n"
		+ "struct Socket { int fd; }\n"
		+ "fn usz? Socket.read(&self, char[] buffer) { return buffer.len; }\n"
		+ "typedef TcpSocket = inline Socket;\n"
		+ "macro usz? maybe(usz x) => x;\n"
		+ "fn usz ident(usz v) => v;\n"
		+ "fn void takes(usz v) {}\n"
		+ "fn void? lock() @maydiscard {}\n"
		+ "macro usz typed(usz x) => x;\n"
		+ "macro untyped(x) => x;\n"
		+ "macro usz Socket.scaled(&self, usz x) => x;\n";

	private List<String> errors(String body)
	{
		myFixture.enableInspections(new C3OptionalUnwrapInspection());
		myFixture.configureByText("app.c3", HEAD + body);
		List<String> errors = myFixture.doHighlighting(HighlightSeverity.ERROR).stream()
			.map(HighlightInfo::getDescription)
			.filter(d -> d != null && (d.startsWith("Optional '") || d.startsWith("The call returns") || d.startsWith("The result of this call")))
			.toList();
		System.out.println("[TEST] errors = " + errors);
		return errors;
	}

	private void applyPropagate(String body, String expectedBody)
	{
		myFixture.enableInspections(new C3OptionalUnwrapInspection());
		myFixture.configureByText("app.c3", HEAD + body);
		myFixture.doHighlighting();
		myFixture.launchAction(myFixture.findSingleIntention("Propagate the fault"));
		myFixture.checkResult(HEAD + expectedBody);
	}

	public void testOptionalCallAssignedToNonOptionalLocalIsError()
	{
		List<String> errors = errors("fn usz read_some(TcpSocket* connection, char[] buffer)\n{\n"
			+ "    usz count = connection.read(buffer);\n    return count;\n}\n");
		assertEquals(errors.toString(), 1, errors.size());
		assertEquals("Optional 'usz?' cannot be assigned to non-optional 'usz'", errors.get(0));
	}

	public void testPropagateAddsBangAndMakesTheFunctionOptional()
	{
		applyPropagate(
			"fn usz read_some(TcpSocket* connection, char[] buffer)\n{\n    usz count = connection.re<caret>ad(buffer);\n    return count;\n}\n",
			"fn usz? read_some(TcpSocket* connection, char[] buffer)\n{\n    usz count = connection.read(buffer)!;\n    return count;\n}\n");
	}

	public void testPropagateKeepsAnAlreadyOptionalFunction()
	{
		applyPropagate(
			"fn usz? read_some(TcpSocket* connection, char[] buffer)\n{\n    usz count = connection.re<caret>ad(buffer);\n    return count;\n}\n",
			"fn usz? read_some(TcpSocket* connection, char[] buffer)\n{\n    usz count = connection.read(buffer)!;\n    return count;\n}\n");
	}

	public void testUnwrappedOrOptionalTargetsAreFine()
	{
		List<String> errors = errors("fn usz? read_some(TcpSocket* connection, char[] buffer)\n{\n"
			+ "    usz a = connection.read(buffer)!;\n"
			+ "    usz b = connection.read(buffer)!!;\n"
			+ "    usz c = connection.read(buffer) ?? 0;\n"
			+ "    usz? d = connection.read(buffer);\n"
			+ "    return a + b + c;\n}\n");
		assertEquals(errors.toString(), 0, errors.size());
	}

	public void testReturningOptionalFromNonOptionalFunctionIsError()
	{
		List<String> errors = errors("fn usz read_some(TcpSocket* connection, char[] buffer)\n{\n"
			+ "    return connection.read(buffer);\n}\n");
		assertEquals(errors.toString(), 1, errors.size());
		assertEquals("Optional 'usz?' cannot be returned from a function returning 'usz'", errors.get(0));
	}

	public void testPropagateOnReturnOnlyChangesTheReturnType()
	{
		applyPropagate(
			"fn usz read_some(TcpSocket* connection, char[] buffer)\n{\n    return connection.re<caret>ad(buffer);\n}\n",
			"fn usz? read_some(TcpSocket* connection, char[] buffer)\n{\n    return connection.read(buffer);\n}\n");
	}

	public void testAssignmentToExistingLocalIsErrorAndPropagates()
	{
		List<String> errors = errors("fn usz read_some(TcpSocket* connection, char[] buffer)\n{\n"
			+ "    usz count;\n    count = connection.read(buffer);\n    return count;\n}\n");
		assertEquals(errors.toString(), 1, errors.size());
		applyPropagate(
			"fn usz read_some(TcpSocket* connection, char[] buffer)\n{\n    usz count;\n    count = connection.re<caret>ad(buffer);\n    return count;\n}\n",
			"fn usz? read_some(TcpSocket* connection, char[] buffer)\n{\n    usz count;\n    count = connection.read(buffer)!;\n    return count;\n}\n");
	}

	public void testOptionalVariableAssignedToNonOptionalIsError()
	{
		List<String> errors = errors("fn usz read_some(TcpSocket* connection, char[] buffer)\n{\n"
			+ "    usz? maybe = connection.read(buffer);\n    usz sure = maybe;\n    return sure;\n}\n");
		assertEquals(errors.toString(), 1, errors.size());
		assertTrue(errors.get(0), errors.get(0).startsWith("Optional 'usz?' cannot be assigned"));
	}

	public void testOptionalMacroResultIsError()
	{
		List<String> errors = errors("fn usz f()\n{\n    usz v = maybe(1);\n    return v;\n}\n");
		assertEquals(errors.toString(), 1, errors.size());
	}

	public void testUnresolvedCalleeStaysQuiet()
	{
		List<String> errors = errors("fn usz f(char[] buffer)\n{\n    usz v = unknown_fn(buffer);\n    return v;\n}\n");
		assertEquals(errors.toString(), 0, errors.size());
	}

	public void testOptionalArgumentMakesAFunctionCallOptional()
	{
		List<String> errors = errors("fn usz f()\n{\n    usz x = ident(maybe(1));\n    return x;\n}\n");
		assertEquals(errors.toString(), 1, errors.size());
		assertEquals("Optional 'usz?' cannot be assigned to non-optional 'usz'", errors.get(0));
		applyPropagate(
			"fn usz f()\n{\n    usz x = ide<caret>nt(maybe(1));\n    return x;\n}\n",
			"fn usz? f()\n{\n    usz x = ident(maybe(1))!;\n    return x;\n}\n");
	}

	public void testNestedOptionalArgumentPropagatesOutward()
	{
		List<String> errors = errors("fn usz f()\n{\n    usz z = ident(ident(maybe(1)));\n    return z;\n}\n");
		assertEquals(errors.toString(), 1, errors.size());
	}

	public void testDiscardedCallWithOptionalArgumentIsError()
	{
		List<String> errors = errors("fn void f()\n{\n    takes(maybe(1));\n}\n");
		assertEquals(errors.toString(), List.of("The result of this call is optional due to its argument(s) and cannot be discarded"), errors);
		applyPropagate(
			"fn void f()\n{\n    tak<caret>es(maybe(1));\n}\n",
			"fn void? f()\n{\n    takes(maybe(1))!;\n}\n");
	}

	public void testDiscardedOptionalResultIsErrorUnlessMayDiscard()
	{
		List<String> errors = errors("fn void f()\n{\n    maybe(1);\n    lock();\n}\n");
		assertEquals(errors.toString(), List.of("The call returns 'usz?', which is optional and must be handled"), errors);
	}

	public void testHandledCallsWithOptionalArgumentsAreFine()
	{
		List<String> errors = errors("fn void? f()\n{\n"
			+ "    takes(maybe(1))!;\n"
			+ "    (void)takes(maybe(1));\n"
			+ "    takes(maybe(1)!);\n"
			+ "    usz x = ident(maybe(1))!;\n"
			+ "}\n");
		assertEquals(errors.toString(), 0, errors.size());
	}

	public void testOptionalArgumentToTypedMacroParameterIsError()
	{
		List<String> errors = errors("fn usz f()\n{\n    usz b = typed(maybe(1));\n    return b;\n}\n");
		assertEquals(errors.toString(), List.of("Optional 'usz?' cannot be passed to parameter 'x' of type 'usz'"), errors);
		applyPropagate(
			"fn usz f()\n{\n    usz b = typed(may<caret>be(1));\n    return b;\n}\n",
			"fn usz? f()\n{\n    usz b = typed(maybe(1)!);\n    return b;\n}\n");
	}

	public void testNamedOptionalArgumentToTypedMacroParameterIsError()
	{
		List<String> errors = errors("fn usz f()\n{\n    usz b = typed(x: maybe(1));\n    return b;\n}\n");
		assertEquals(errors.toString(), 1, errors.size());
	}

	public void testOptionalArgumentToUntypedMacroParameterIsQuiet()
	{
		List<String> errors = errors("fn usz? f()\n{\n    usz a = untyped(maybe(1))!;\n    return a;\n}\n");
		assertEquals(errors.toString(), 0, errors.size());
	}

	public void testMethodMacroMapsArgumentsPastSelf()
	{
		List<String> errors = errors("fn usz f(Socket* s)\n{\n    usz y = s.scaled(maybe(1));\n    return y;\n}\n");
		assertEquals(errors.toString(), List.of("Optional 'usz?' cannot be passed to parameter 'x' of type 'usz'"), errors);
	}
}
