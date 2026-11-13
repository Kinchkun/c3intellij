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
			.filter(d -> d != null && (d.startsWith("Optional '") || d.startsWith("The call returns") || d.startsWith("The result of this call")
				|| d.startsWith("This expression rethrows") || d.startsWith("The macro returns")))
			.toList();
		System.out.println("[TEST] errors = " + errors);
		return errors;
	}

	private void applyPropagate(String body, String expectedBody)
	{
		applyFix("Propagate the fault", body, expectedBody);
	}

	private void applyFix(String fixName, String body, String expectedBody)
	{
		myFixture.enableInspections(new C3OptionalUnwrapInspection());
		myFixture.configureByText("app.c3", HEAD + body);
		myFixture.doHighlighting();
		myFixture.launchAction(myFixture.findSingleIntention(fixName));
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

	public void testFixOnReturnOnlyChangesTheReturnType()
	{
		applyFix("Change return type to 'usz?'",
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

	private static final String FAULTS = "faultdef CURL_INIT_FAILED;\nstruct HttpClient { int dummy; }\n";

	/** `return FAULT~;` in a function declared without `?`: the compiler cannot cast 'void?' to 'void'. */
	public void testRaisedFaultReturnedFromVoidFunctionIsError()
	{
		List<String> errors = errors(FAULTS + "fn void HttpClient.init(&self)\n{\n"
			+ "    if (!self.dummy)\n    {\n        return CURL_INIT_FAILED~;\n    }\n}\n");
		assertEquals(errors.toString(), List.of("Optional 'void?' cannot be returned from a function returning 'void'"), errors);
	}

	public void testRaisedFaultReturnedFromTypedFunctionAndMacroIsError()
	{
		List<String> errors = errors(FAULTS
			+ "fn int f(bool flag)\n{\n    if (flag) return CURL_INIT_FAILED~;\n    return 1;\n}\n"
			+ "macro HttpClient* m()\n{\n    return app::CURL_INIT_FAILED~;\n}\n");
		assertEquals(errors.toString(), List.of(
			"Optional 'int?' cannot be returned from a function returning 'int'",
			"Optional 'HttpClient*?' cannot be returned from a function returning 'HttpClient*'"), errors);
	}

	public void testRaisedFaultInOptionalOrInferredCallableIsFine()
	{
		List<String> errors = errors(FAULTS
			+ "fn void? f()\n{\n    return CURL_INIT_FAILED~;\n}\n"
			+ "fn int? g()\n{\n    return CURL_INIT_FAILED~;\n}\n"
			+ "macro inferred()\n{\n    return CURL_INIT_FAILED~;\n}\n"
			+ "fn void h()\n{\n    int? value = CURL_INIT_FAILED~;\n    return;\n}\n");
		assertEquals(errors.toString(), 0, errors.size());
	}

	public void testRaisedFaultAssignedToNonOptionalLocalIsError()
	{
		List<String> errors = errors(FAULTS + "fn void f()\n{\n    int value = CURL_INIT_FAILED~;\n}\n");
		assertEquals(errors.toString(), List.of("Optional 'int?' cannot be assigned to non-optional 'int'"), errors);
	}

	public void testFixChangesVoidReturnTypeToOptional()
	{
		applyFix("Change return type to 'void?'",
			FAULTS + "fn void HttpClient.init(&self)\n{\n    if (!self.dummy)\n    {\n        return CURL_INIT_<caret>FAILED~;\n    }\n}\n",
			FAULTS + "fn void? HttpClient.init(&self)\n{\n    if (!self.dummy)\n    {\n        return CURL_INIT_FAILED~;\n    }\n}\n");
	}

	public void testFixChangesTypedReturnTypeToOptional()
	{
		applyFix("Change return type to 'HttpClient*?'",
			FAULTS + "fn HttpClient* create()\n{\n    return CURL_INIT_<caret>FAILED~;\n}\n",
			FAULTS + "fn HttpClient*? create()\n{\n    return CURL_INIT_FAILED~;\n}\n");
	}

	private static final String CURL = "faultdef CURL_INIT_FAILED;\nstruct Curl { int handle; }\n"
		+ "struct HttpClient { Curl* curl; int retries; }\nfn Curl* easy_init() => null;\n";

	/** `a ?: FAULT~` is optional; a struct member is not: "It is not possible to cast from 'Curl*?' to 'Curl*'". */
	public void testElvisWithRaisedFaultAssignedToMemberIsError()
	{
		List<String> errors = errors(CURL + "fn void? HttpClient.init(&self)\n{\n"
			+ "    self.curl = easy_init() ?: CURL_INIT_FAILED~;\n}\n");
		assertEquals(errors.toString(), List.of("Optional 'Curl*?' cannot be assigned to non-optional 'Curl*'"), errors);
	}

	public void testPropagateOnElvisAssignedToMemberAddsBang()
	{
		applyPropagate(
			CURL + "fn void? HttpClient.init(&self)\n{\n    self.curl = easy_<caret>init() ?: CURL_INIT_FAILED~;\n}\n",
			CURL + "fn void? HttpClient.init(&self)\n{\n    self.curl = easy_init() ?: CURL_INIT_FAILED~!;\n}\n");
	}

	public void testOptionalChoicesAssignedToNonOptionalAreErrors()
	{
		List<String> errors = errors(CURL + "fn void? f(bool flag, HttpClient* client)\n{\n"
			+ "    int a = flag ? 1 : CURL_INIT_FAILED~;\n"
			+ "    client.retries = maybe(1) ?? CURL_INIT_FAILED~;\n"
			+ "    client.curl = flag ? easy_init() : CURL_INIT_FAILED~;\n"
			+ "    usz b = flag ? maybe(1) : 2;\n}\n");
		assertEquals(errors.toString(), List.of(
			"Optional 'int?' cannot be assigned to non-optional 'int'",
			"Optional 'int?' cannot be assigned to non-optional 'int'",
			"Optional 'Curl*?' cannot be assigned to non-optional 'Curl*'",
			"Optional 'usz?' cannot be assigned to non-optional 'usz'"), errors);
	}

	public void testHandledChoicesAndPlainMemberAssignmentsAreFine()
	{
		List<String> errors = errors(CURL + "fn void? f(bool flag, HttpClient* client)\n{\n"
			+ "    client.curl = easy_init() ?: CURL_INIT_FAILED~!;\n"
			+ "    client.curl = easy_init();\n"
			+ "    client.retries = flag ? 1 : 2;\n"
			+ "    usz a = maybe(1) ?? 0;\n"
			+ "    int? b = flag ? 1 : CURL_INIT_FAILED~;\n}\n");
		assertEquals(errors.toString(), 0, errors.size());
	}

	/** "This expression is doing a rethrow, but 'init' returns 'void', which isn't an optional type." */
	public void testRethrowInNonOptionalFunctionIsError()
	{
		List<String> errors = errors(CURL + "fn void HttpClient.init(&self)\n{\n"
			+ "    self.curl = easy_init() ?: CURL_INIT_FAILED~!;\n}\n"
			+ "fn usz plain()\n{\n    usz value = maybe(1)!;\n    return value;\n}\n"
			+ "macro usz typed_macro()\n{\n    return maybe(1)!;\n}\n");
		assertEquals(errors.toString(), List.of(
			"This expression rethrows with '!', but the function returns 'void', which is not optional",
			"This expression rethrows with '!', but the function returns 'usz', which is not optional",
			"This expression rethrows with '!', but the macro returns 'usz', which is not optional"), errors);
	}

	public void testRethrowInOptionalOrInferredCallableIsFine()
	{
		List<String> errors = errors(CURL + "fn void? HttpClient.init(&self)\n{\n"
			+ "    self.curl = easy_init() ?: CURL_INIT_FAILED~!;\n}\n"
			+ "macro inferred()\n{\n    return maybe(1)!;\n}\n"
			+ "fn void checks()\n{\n    usz forced = maybe(1)!!;\n    bool defined = $defined(maybe(1)!);\n}\n");
		assertEquals(errors.toString(), 0, errors.size());
	}

	public void testFixOnRethrowChangesTheReturnType()
	{
		applyFix("Change return type to 'void?'",
			CURL + "fn void HttpClient.init(&self)\n{\n    self.curl = easy_init() ?: CURL_INIT_<caret>FAILED~!;\n}\n",
			CURL + "fn void? HttpClient.init(&self)\n{\n    self.curl = easy_init() ?: CURL_INIT_FAILED~!;\n}\n");
	}

	public void testFixOnRethrowReplacesItWithForceUnwrap()
	{
		applyFix("Replace '!' with '!!'",
			CURL + "fn usz plain()\n{\n    usz value = may<caret>be(1)!;\n    return value;\n}\n",
			CURL + "fn usz plain()\n{\n    usz value = maybe(1)!!;\n    return value;\n}\n");
	}

	/** After `if (catch value) return` (or inside `if (try value)`) the variable is unwrapped. */
	public void testVariableTestedWithCatchOrTryIsFine()
	{
		List<String> errors = errors(CURL + "fn usz f(HttpClient* client)\n{\n"
			+ "    usz? count = maybe(1);\n    if (catch count) return 0;\n"
			+ "    usz sure = count;\n    client.retries = (int)ident(count);\n"
			+ "    usz? other = maybe(2);\n    if (try other) return other;\n"
			+ "    return sure;\n}\n");
		assertEquals(errors.toString(), 0, errors.size());
	}

	private static final String OK_MACRO = "faultdef CURL_ERROR;\nfn int easy_setopt(int option) => 0;\n"
		+ "macro @ok(#call)\n{\n    var result = #call;\n    if (result)\n    {\n        return CURL_ERROR~;\n    }\n    return result;\n}\n"
		+ "macro @rethrowing(#call)\n{\n    usz value = maybe(1)!;\n    return #call;\n}\n"
		+ "macro @short() => @ok(easy_setopt(1));\n"
		+ "macro @plain(#call)\n{\n    var result = #call;\n    return result;\n}\n";

	/** A macro without a written return type that returns `FAULT~` returns an optional: "The macro returns 'int?', which is an optional and must be handled". */
	public void testMacroInferringAnOptionalMustBeHandled()
	{
		List<String> errors = errors(OK_MACRO + "fn void? execute()\n{\n"
			+ "    @ok(easy_setopt(1));\n"
			+ "    @rethrowing(easy_setopt(1));\n"
			+ "    @short();\n"
			+ "    int value = @ok(easy_setopt(1));\n}\n");
		assertEquals(errors.toString(), List.of(
			"The macro returns an optional, which must be handled",
			"The macro returns an optional, which must be handled",
			"The macro returns an optional, which must be handled",
			"Optional 'int?' cannot be assigned to non-optional 'int'"), errors);
	}

	public void testHandledOrPlainInferredMacroIsFine()
	{
		List<String> errors = errors(OK_MACRO + "fn void? execute()\n{\n"
			+ "    @ok(easy_setopt(1))!;\n"
			+ "    (void)@ok(easy_setopt(1));\n"
			+ "    int value = @ok(easy_setopt(1))!!;\n"
			+ "    @plain(easy_setopt(1));\n"
			+ "    int other = @plain(easy_setopt(1));\n}\n");
		assertEquals(errors.toString(), 0, errors.size());
	}

	public void testPropagateOnInferredMacroAddsBang()
	{
		applyPropagate(
			OK_MACRO + "fn void? execute()\n{\n    @o<caret>k(easy_setopt(1));\n}\n",
			OK_MACRO + "fn void? execute()\n{\n    @ok(easy_setopt(1))!;\n}\n");
	}
}
