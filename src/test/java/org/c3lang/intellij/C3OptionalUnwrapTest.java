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
		+ "macro usz? maybe(usz x) => x;\n";

	private List<String> errors(String body)
	{
		myFixture.enableInspections(new C3OptionalUnwrapInspection());
		myFixture.configureByText("app.c3", HEAD + body);
		List<String> errors = myFixture.doHighlighting(HighlightSeverity.ERROR).stream()
			.map(HighlightInfo::getDescription)
			.filter(d -> d != null && d.startsWith("Optional '"))
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
}
