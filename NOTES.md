# Maintainer notes

## Post-generation patches (re-apply after regenerating the parser/PSI)

The parser and PSI under `src/main/gen/` are generated from `src/main/java/org/c3lang/intellij/C3.bnf`
(and `C3.flex`) via the **Grammar-Kit** IDE action. A few fixes cannot be expressed in the
`.bnf` and live in the generated code, so **they are overwritten whenever the grammar is
regenerated and must be re-applied**.

### 1. `generic_parameter` must not match assignment/ternary expressions

**File:** `src/main/gen/org/c3lang/intellij/parser/C3Parser.java`
**Method:** `generic_parameter(PsiBuilder b, int l)`

**Change:** the generated body calls `expr` at precedence `-1` (which includes assignment and
ternary). Change the precedence argument to `2`:

```java
// generated (after regeneration):
r = expr(b, l + 1, -1);

// patched (re-apply this):
r = expr(b, l + 1, 2);
```

**Why:** C3 generic instantiation uses braces (`Foo{int}`), the same token as a declaration
body. With precedence `-1`, an assignment expression is a valid generic argument, so

```c3
constdef HttpHeader : inline String {
    CONTENT_TYPE = "content-type"
}
```

is misparsed as the generic type `String{ CONTENT_TYPE = "content-type" }` — the body is
swallowed, the parser then "expects" the real `{`, and reports an error on the following
declaration (also corrupting indentation for the structure/formatter). Precedence `2`
excludes assignment and ternary (neither is ever a valid generic argument), so the brace is
correctly left as the declaration body. See the `NOTE` comment at `generic_parameter` in
`C3.bnf`.

**Permanent alternative:** convert `generic_parameter` to a hand-written Grammar-Kit external
rule (`generic_parameter ::= <<parseGenericParameter>>`) backed by a static method in a
maintained `*ParserUtil` class. That source is not regenerated, so the fix would survive.

### 2. `C3StringExprImpl` is a `PsiLanguageInjectionHost`

**File:** `src/main/gen/org/c3lang/intellij/psi/impl/C3StringExprImpl.java`

`string_expr` has no grammar mixin, so language injection (the `@language <param>: <lang>`
doc-comment feature, see `injection/C3LanguageInjector`) requires the generated impl to
implement `com.intellij.psi.PsiLanguageInjectionHost`. After regenerating, re-add to the class:

- `implements ... C3StringExpr, PsiLanguageInjectionHost`
- `isValidHost()` — true for `"`/`` ` ``-delimited literals
- `updateText(String)` — replaces the first leaf via `LeafElement.replaceWithText`
- `createLiteralTextEscaper()` — a 1:1 `LiteralTextEscaper` over the host text

**Permanent alternative:** add `mixin("string_expr")` to `C3.bnf` pointing at a maintained
`C3StringExprMixinImpl` that implements `PsiLanguageInjectionHost`; that source is not
regenerated, so the fix would survive.

## Regenerating

1. Open `C3.bnf` (and/or `C3.flex`) in the IDE with the Grammar-Kit / JFlex plugins installed.
2. Right-click → *Generate Parser Code* (and *Run JFlex Generator* for the lexer).
3. Re-apply every patch listed above.
4. `./gradlew compileJava` to verify.
