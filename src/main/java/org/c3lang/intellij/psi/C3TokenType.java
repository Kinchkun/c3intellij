package org.c3lang.intellij.psi;

import com.intellij.psi.tree.IElementType;
import org.c3lang.intellij.C3Language;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Christoffer Lerno
 */
public class C3TokenType extends IElementType
{
    /** Human-readable names used in parser error messages (e.g. "';' expected"). */
    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
        Map.entry("EOS", "';'"),
        Map.entry("LB", "'{'"), Map.entry("RB", "'}'"),
        Map.entry("LP", "'('"), Map.entry("RP", "')'"),
        Map.entry("LBT", "'['"), Map.entry("RBT", "']'"),
        Map.entry("LVEC", "'<['"), Map.entry("RVEC", "']>'"),
        Map.entry("COMMA", "','"), Map.entry("COLON", "':'"),
        Map.entry("DOT", "'.'"), Map.entry("DOTDOT", "'..'"), Map.entry("ELLIPSIS", "'...'"),
        Map.entry("SCOPE", "'::'"), Map.entry("EQ", "'='"), Map.entry("IMPLIES", "'=>'"),
        Map.entry("LT_OP", "'<'"), Map.entry("GT_OP", "'>'"),
        Map.entry("LE_OP", "'<='"), Map.entry("GE_OP", "'>='"),
        Map.entry("EQ_OP", "'=='"), Map.entry("NE_OP", "'!='"),
        Map.entry("AND", "'&&'"), Map.entry("OR", "'||'"),
        Map.entry("PLUS", "'+'"), Map.entry("MINUS", "'-'"),
        Map.entry("STAR", "'*'"), Map.entry("DIV", "'/'"), Map.entry("MOD", "'%'"),
        Map.entry("AMP", "'&'"), Map.entry("BIT_OR", "'|'"), Map.entry("BIT_XOR", "'^'"),
        Map.entry("BIT_NOT", "'~'"), Map.entry("BANG", "'!'"), Map.entry("QUESTION", "'?'"),
        Map.entry("IDENT", "identifier"), Map.entry("TYPE_IDENT", "type name"),
        Map.entry("CONST_IDENT", "constant name"), Map.entry("AT_IDENT", "@attribute"),
        Map.entry("CT_IDENT", "$identifier")
    );

    public C3TokenType(@NotNull String debugName)
    {
        super(debugName, C3Language.INSTANCE);
    }

    @Override
    public String toString()
    {
        String name = super.toString();

        String display = DISPLAY_NAMES.get(name);
        if (display != null) return display;

        // Keywords: KW_FN -> 'fn', KW_CT_IF -> '$if'.
        if (name.startsWith("KW_"))
        {
            String keyword = name.substring(3);
            if (keyword.startsWith("CT_")) keyword = "$" + keyword.substring(3);
            return "'" + keyword.toLowerCase() + "'";
        }

        return name;
    }
}
