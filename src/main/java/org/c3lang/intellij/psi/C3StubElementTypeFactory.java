package org.c3lang.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.stubs.IStubElementType;
import org.c3lang.intellij.stubs.*;

/**
 * Maps a grammar rule name to the stub element type that should handle it.
 *
 * <p>{@code C3.bnf} declares which rules are stub-backed via its {@code elementTypeFactory(...)=
 * stubFactory} directive listing rule names like {@code func_def|module|type_name|...}. The generated
 * parser then calls {@link #stubFactory(String)} for each such rule to obtain the right
 * {@link IStubElementType} (e.g. {@link C3FuncDefElementType}). In other words, this is the lookup
 * table that turns "this rule should be indexed" into "use this stub machinery".
 *
 * <p>To make a new declaration stub-backed you: add it to the {@code elementTypeFactory} list in the
 * grammar, add a constant + {@code case} here, and create the matching {@code *Stub}/{@code *ElementType}
 * pair in the {@code stubs} package.
 */
public class C3StubElementTypeFactory {
    private final static Logger log = Logger.getInstance(C3StubElementTypeFactory.class);

    public static final String FUNC_DEF = "FUNC_DEF";
    public static final String MODULE = "MODULE";
    public static final String TYPE_NAME = "TYPE_NAME";
    public static final String MACRO_DEFINITION = "MACRO_DEFINITION";
    public static final String CONST_DECLARATION_STMT = "CONST_DECLARATION_STMT";
    public static final String STRUCT_MEMBER_DECLARATION = "STRUCT_MEMBER_DECLARATION";
    public static final String STRUCT_DECLARATION = "STRUCT_DECLARATION";
    public static final String ENUM_CONSTANT = "ENUM_CONSTANT";
	public static final String CONSTDEF_CONSTANT = "CONSTDEF_CONSTANT";
    public static final String FAULT_DEFINITION = "FAULT_DEFINITION";
	public static final String ATTRDEF_DECL = "ATTRDEF_DECL";

    public static IStubElementType<?, ?> stubFactory(String name) {
        final C3StubElementType<?, ?> type = switch (name) {
            case FUNC_DEF -> C3FuncDefElementType.getInstance();
            case MODULE -> C3ModuleElementType.getInstance();
            case TYPE_NAME -> C3TypeNameElementType.getInstance();
            case MACRO_DEFINITION -> C3MacroDefinitionElementType.getInstance();
            case CONST_DECLARATION_STMT -> C3ConstDeclarationStmtElementType.getInstance();
            case STRUCT_MEMBER_DECLARATION -> C3StructMemberDeclarationElementType.getInstance();
            case STRUCT_DECLARATION -> C3StructDeclarationElementType.getInstance();
            case ENUM_CONSTANT -> C3EnumConstantElementType.getInstance();
			case CONSTDEF_CONSTANT -> C3ConstdefConstantElementType.getInstance();
            case FAULT_DEFINITION -> C3FaultDefinitionElementType.getInstance();
			case ATTRDEF_DECL -> C3AttrdefDeclElementType.getInstance();
            default -> null;
        };
        if (type == null) {
            log.warn("Unknown stub factory: " + name);
        }
        return type;
    }

}