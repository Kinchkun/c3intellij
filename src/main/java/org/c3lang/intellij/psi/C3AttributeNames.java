package org.c3lang.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Reads attribute lists such as {@code @builtin @noreturn} by simple name. */
public final class C3AttributeNames
{
	private C3AttributeNames()
	{
	}

	/** Whether {@code attributes} contains {@code @name}, ignoring case and any module path on the attribute. */
	public static boolean has(@Nullable C3Attributes attributes, @NotNull String name)
	{
		if (attributes == null) return false;
		for (C3Attribute attribute : attributes.getAttributeList())
		{
			String text = attribute.getAttributeName().getText();
			String simple = text.substring(text.lastIndexOf(':') + 1).replace("@", "").toLowerCase(Locale.ROOT);
			if (simple.equals(name)) return true;
		}
		return false;
	}
}
