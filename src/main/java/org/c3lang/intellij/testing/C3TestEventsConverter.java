package org.c3lang.intellij.testing;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.openapi.util.Key;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Feeds the test tree from plain C3 test runner output: each line goes through
 * {@link C3TestOutputParser}, whose events are re-emitted as service messages. Tests are grouped
 * into one suite per module and located via {@link C3TestLocator}.
 */
final class C3TestEventsConverter extends OutputToGeneralTestEventsConverter
{
	private final C3TestOutputParser parser = new C3TestOutputParser(new Emitter());

	private @Nullable ServiceMessageVisitor visitor;
	private @NotNull Key<?> outputType = Key.create("stdout");
	private boolean handled;
	private @Nullable String openSuite;

	C3TestEventsConverter(@NotNull String testFrameworkName, @NotNull TestConsoleProperties consoleProperties)
	{
		super(testFrameworkName, consoleProperties);
	}

	@Override
	protected boolean processServiceMessages(@NotNull String text, @NotNull Key<?> outputType,
	                                         @NotNull ServiceMessageVisitor visitor) throws ParseException
	{
		this.visitor = visitor;
		this.outputType = outputType;
		handled = true;
		parser.line(text);
		// Lines outside tests (compiler output, the summary) go to the console as they are.
		return handled;
	}

	@Override
	public void flushBufferOnProcessTermination(int exitCode)
	{
		super.flushBufferOnProcessTermination(exitCode);
		if (visitor == null) return;
		parser.finish();
		closeSuite();
	}

	private void emit(@NotNull Message message)
	{
		if (visitor == null) return;
		try
		{
			super.processServiceMessages(message.toString(), outputType, visitor);
		}
		catch (ParseException ignored)
		{
			// Built by ServiceMessageBuilder, so always well-formed.
		}
	}

	private void closeSuite()
	{
		if (openSuite == null) return;
		emit(Message.testSuiteFinished(openSuite));
		openSuite = null;
	}

	/** A TeamCity service message: {@code ##teamcity[testStarted name='...' locationHint='...']}. */
	static final class Message
	{
		private final String type;
		private final Map<String, String> attributes = new LinkedHashMap<>();

		private Message(@NotNull String type, @NotNull String name)
		{
			this.type = type;
			attributes.put("name", name);
		}

		static Message testSuiteStarted(String name) { return new Message("testSuiteStarted", name); }
		static Message testSuiteFinished(String name) { return new Message("testSuiteFinished", name); }
		static Message testStarted(String name) { return new Message("testStarted", name); }
		static Message testStdOut(String name) { return new Message("testStdOut", name); }
		static Message testFailed(String name) { return new Message("testFailed", name); }
		static Message testFinished(String name) { return new Message("testFinished", name); }

		Message addAttribute(@NotNull String key, @NotNull String value)
		{
			attributes.put(key, value);
			return this;
		}

		@Override
		public String toString()
		{
			StringBuilder result = new StringBuilder("##teamcity[").append(type);
			attributes.forEach((key, value) -> result.append(' ').append(key).append("='").append(escape(value)).append('\''));
			return result.append(']').toString();
		}

		static @NotNull String escape(@NotNull String value)
		{
			StringBuilder result = new StringBuilder(value.length());
			for (char c : value.toCharArray())
			{
				switch (c)
				{
					case '|' -> result.append("||");
					case '\'' -> result.append("|'");
					case '\n' -> result.append("|n");
					case '\r' -> result.append("|r");
					case '[' -> result.append("|[");
					case ']' -> result.append("|]");
					default -> result.append(c);
				}
			}
			return result.toString();
		}
	}

	private final class Emitter implements C3TestOutputParser.Listener
	{
		@Override
		public void testStarted(@NotNull String name)
		{
			String module = C3TestOutputParser.moduleOf(name);
			if (!module.equals(openSuite))
			{
				closeSuite();
				if (!module.isEmpty())
				{
					emit(Message.testSuiteStarted(module)
						.addAttribute("locationHint", C3TestLocator.SUITE_PROTOCOL + "://" + module));
					openSuite = module;
				}
			}
			emit(Message.testStarted(C3TestOutputParser.functionOf(name))
				.addAttribute("locationHint", C3TestLocator.TEST_PROTOCOL + "://" + name));
		}

		@Override
		public void testOutput(@NotNull String name, @NotNull String text)
		{
			emit(Message.testStdOut(C3TestOutputParser.functionOf(name)).addAttribute("out", text));
		}

		@Override
		public void testFailed(@NotNull String name, @NotNull String message, @NotNull String details)
		{
			emit(Message.testFailed(C3TestOutputParser.functionOf(name))
				.addAttribute("message", message)
				.addAttribute("details", details));
		}

		@Override
		public void testFinished(@NotNull String name, long durationMillis)
		{
			Message finished = Message.testFinished(C3TestOutputParser.functionOf(name));
			if (durationMillis >= 0) finished.addAttribute("duration", Long.toString(durationMillis));
			emit(finished);
		}

		@Override
		public void unhandled(@NotNull String line)
		{
			handled = false;
		}
	}
}
