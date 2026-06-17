<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# C3IntelliJ Changelog

## [Unreleased]
- Completion inside a declaration's interface list (`struct MyStruct (…)`) offers the available interfaces (only interface types), adding the import if needed. Works even before the declaration body is typed.
- Fixed an `AssertionError` thrown by the annotator on an incomplete `alias` declaration while typing.
- Quick Documentation on a type used in a parameter/return type (e.g. `Formatter` in `Formatter* formatter`) now shows the type's documentation instead of the enclosing function's.
- The gutter run marker on `main`/`@test` functions is now anchored on the function-name leaf (fixes a "LineMarker is supposed to be registered for leaf elements only" performance warning).
- Bumped the stub-index version so the IDE rebuilds the index on update, clearing the stale-index state that logged "Stub index points to a file … without indexed stub tree" and broke highlighting until a restart.
- Language injection: documenting a parameter with `<* @language <param>: <lang> *>` injects that language (JSON, XML, SQL, HTML, YAML, RegExp, …) into the matching string argument at every call site, so `fn("…")` is highlighted and analysed as that language. With SQL and a configured data source, the platform resolves the SQL against the database automatically. Works for positional and named arguments, free functions and `obj.method` calls.
- Completion offers in-scope local variables and function/macro parameters, ranked above functions and types; a lower-case prefix no longer suggests (UpperCamelCase) type names.
- Member completion (`obj.`) now works when the receiver is a function/macro parameter (e.g. a `Formatter* formatter` parameter), not only a local variable; pointer parameter types resolve to the pointee. Interface-typed receivers additionally list the interface's own and inherited methods.
- Inspection: a struct that declares an interface but is missing its (non-`@optional`) methods is flagged, with an "Implement interface methods" quick fix (Alt+Enter) that generates `@dynamic` stubs (with an `unreachable()` body so they compile).
- Intention (Alt+Enter): "Implement optional interface methods" — pick the interface's `@optional` methods from a popup and generate their stubs.
- All C3 run configurations are grouped under a single "C3" node in the New Run Configuration popup.
- Friendlier parser error messages (e.g. `';' expected` instead of `C3TokenType.EOS expected`).
- Go to definition, Find Usages and Quick Documentation for the interface name in `struct X (Interface)`.
- Quick Documentation works when invoked directly on a declaration's name (e.g. an `alias`/`struct`/`interface`), not only on references; fixed it hijacking interface-reference docs.
- Doc comments (`<* ... *>`): prefix-aware completion of contract annotations (`@param`, `@return`, `@return?`, `@require`, `@ensure`, `@deprecated`, `@pure`), `@param` reference modifiers (`[in]`, `[out]`, …) and parameter names; `@require`/`@ensure` expressions are syntax-highlighted as code; typing `<*` inserts the closing `*>`; Enter continues the comment with the one-space indentation.
- Fixed `constdef`/`enum` bodies being misparsed as generic arguments (`String { CONST = ... }`), which caused a spurious parse error and wrong indentation of the following declaration.
- C3 name-index lookups no longer crash on a stale/inconsistent stub index (degrade gracefully; Invalidate Caches resolves the underlying issue).
- Richer syntax highlighting: `@attribute`/`@macro` and `$compile-time` identifiers are coloured, function and method *call* sites are highlighted, and bundled default colours make types/functions/methods visible out of the box (light + dark).
- Run and debug C3 scratch files: the gutter ▶ on `main` in a scratch runs it via `c3c compile-run`, and the resulting "C3 Single File" configuration can be debugged under LLDB (CLion).
- Fixed "Unexpected termination offset" lexer crash when editing C3 files/scratches (highlighter lexer state is now reset on restart and never reports a token past end-of-stream).
- Native debugging in CLion (LLDB): breakpoints in C3 files, Debug for "C3 Run Project" and "C3 Test", and bundled `c3.py` so `String` values render in Variables/Watches. Watches, inspect and set-variable come from CLion's debugger. Gated to CLion; the plugin still works in other IDEs.
- Code formatter: Reformat Code and automatic indentation inside `{ }` blocks (including on Enter).
- Formatter indents wrapped contents of `( )` / `[ ]` (e.g. multi-line call arguments and conditions).
- Formatter spacing: `while(`/`if(`/`for(` (no space before the parenthesis), one space after `,`, no space before `,`/`;`, no padding inside `( )`.
- Find usages / rename / goto declaration works for locals.
- Find definition of module from import.
- Structure tool window for C3 files; methods are nested under their owning type.
- Go to Symbol / Search Everywhere (Symbols) finds C3 types, functions, macros, constants and faults by name (and no longer lists the interface usage in `struct X (Interface)` as a duplicate symbol).
- Gutter run button on `@test` functions to run a single test.
- Clickable `file.c3:line` links in run/test console output (e.g. failing assertions).
- Run configurations for `c3c test` and `c3c docgen`.
- Code completion for methods on `obj.` (not only struct fields).
- Go to definition for method calls (`obj.method()`).
- Go to definition and Quick Documentation for `Type.CONSTANT` access (constdef constants and enum constants).
- Quick documentation for structs, enums, bitstructs, faults, typedefs, aliases, attrdefs, constdefs, enum/constdef constants and modules.
- Syntax highlighting for operators; configurable colors for operators, comments, escape sequences and bytes.
- Fixed NullPointerException resolving cross-module types (broke goto/quick-doc on imported types).
- Fixed IndexNotReadyException crash when indexing structs with cross-module field types.
- Fixed NullPointerException showing quick documentation for parameterless functions/macros.

## [0.2.3]
- Correctly handle `$defined(Random r = random)`
- Type aliases to function were incorrectly flagged as incorrect.
- Import suggestion on std::thread::channel even if std::thread was imported.

## [0.2.2] - 2026-06-04
- `$assert`, `$error` now work with vaargs.
- Updated new file syntax
- Various fixes to code completion.

## [0.2.1] - 2026-04-21
- 0.8.0 Syntax compatibility

## [0.2.0] - 2026-03-26
- IntelliJ 2026 compatibility
- Handle `$defined(int a = 123)`
- Support new generics syntax.
- Fix crash when backtracking on a definition.
- Support int as expression.
- Support attributes on `var foo = 123`

## [0.1.8] - 2025-12-19
- Support ???:
- Support foo = ...
- Support trailing , in parameter list.
- Support unary !!.
- Support #! shebang comment
- Support experimental ~ postfix.

## [0.1.7] - 2025-08-05

- Fixes to `$Type = <expr>` and `alias module`

## [0.1.6] - 2025-08-01

- Support 1LL. Remove support for 1u64.
- Support const enums.
- Support inline enum types.
- Support `$Type = <expr>` works.
- Support `@operator(+)`.
- Support `alias foo = module abc`.

## [0.1.5] - 2025-04-25

- Fixes $Type in params.

## [0.1.4] - 2025-04-24

### Fixes
- Fixed doc comments not finding macro parameters
- Fixed doc comment completions
- Fixed IDE error caused by doc comment not finding underlying function or macro
- Fixed doc comment not finding underlying function or macro when in default module
- Fixed top level code completion interfering with doc comments
- Fixed hover doc displaying parameters that only existed in the doc comment

### Additions
- Added hover doc for macros
- Added doc comment description to hover doc
- Added inspection for missing imports
- Added inspection for missing functions or macro in imports
- Added stdlib path selector in both project wizards
- Added banner that shows if the stdlib path couldn't be detected
- Added string highlighting in doc comments for all strings
- Added settings page
- Added styled parameter names in doc comments

### Optimizations
- Optimized stdlib path lookup by the IDE
- Optimized code completion by implementing new completion system

## [0.1.3] - 2025-04-16

- Documentation on hover.
- Correctly highlight overloads.

## [0.1.2] - 2025-04-04

- Line marker for main.
- Color selector for #ff00aa.

## [0.1.1] - 2025-04-04

- Added new file action for c3 projects
- Fixed project.json target always being called test -> now is the project name

## [0.1.0] - 2025-04-02

- Full 0.7.0 support: faultdef, typedef etc.
- Find usages and rename.
- Create project wizard.
- Color configuration for `return FAULT?;` 

## [0.0.26] - 2025-03-15

- Support for typedef, attrdef, faultdef. 
- Fixes issues with `@pool() =>`, `$exec` and `$Type = int`.

## [0.0.25] - 2025-03-13

Please Use 0.0.24 and 0.0.23 for 0.6.8 and earlier.

- Update `$foreach` and `if` syntax with the changes from 0.7.0.
- Update to new `int?` syntax.
- Update to new fault definitions.
- `alias` replaces `def`.

## [0.0.24] - 2025-03-09

- Code completion for struct, struct fields, union, enum, const, import, functions, macros
- Goto declaration for struct, struct fields, union, enum, const, functions, macros
- Rename identifiers
- Add import QuickFix
- Updated with Foo{int} generic syntax. Removed {| |} and (< >).
- Removed $varef and & arguments for macros. 

## [0.0.23] - 2025-01-28

- Function and import completion
- Support experimental <[]> syntax.

## [0.0.22] - 2025-01-20

- More IntelliJ compatibility updates.

## [0.0.21] - 2025-01-16

- IntelliJ compatibility

## [0.0.20] - 2025-01-03

- Working "run" profiles.

## [0.0.19] - 2024-12-30

- Fix `?!!` syntax. 

## [0.0.18] - 2024-12-15

- Allow for experimental array syntax.
- Remove use of deprecated functions.

## [0.0.17] - 2024-10-09

- Support `<* *>` doc comments.
- Fix syntax for `-` in asm blocks.
- Fix syntax for bytes blocks.

## [0.0.16] - 2024-09-05

- Added `+++`, `&&&`, `|||` support.
- Added support for new named parameters.
- Updated $va-expression syntax.
- Removed deprecated `$or` `$and` `$concat` `$append`.

## [0.0.15] - 2024-07-03

- Added `$concat` and `$append` support.
- Support `{ .foo, .bar }` bitstruct initialization.
- Support `defer (catch err)`
- Support of `213L`

## [0.0.14] - 2024-06-12

### Updated

- 0.6.0 compatibility: Support new syntax for enums.

## [0.0.13] - 2023-10-25

### Updated

- Support `$feature`, `$is_const`, `$and`, `$or`.
- Support `asm` attributes.
- Support new `$defined`.
- Support `interface`.
- Remove support for `$checks`.

## [0.0.12] - 2023-07-24

### Updated

- Remove assert(try ...)
- Support `nextcase default`.

## [0.0.11] - 2023-07-06

### Updated

- Support new generics syntax.

## [0.0.10] - 2023-07-02

### Fixed

- Fixed incorrect parsing of integer generics.

## [0.0.9] - 2023-06-24

### Updated
- `assert` now accepts printf style arguments.

## [0.0.8] - 2023-06-19

### Updated

- `def` syntax annotation updated.
- `define` and `typedef` removed.
- Updated `$include` syntax.
- Fix of `.#x` syntax

## [0.0.7] - 2023-05-15

### Added

- Pair quotes.
- Initial run configuration.
- Breadcrumbs for some constructs.

### Fixed

- Incorrect parsing of `def` with generic parameters.

## [0.0.6] - 2023-05-11

### Added

- Allow IDE .c3 file association.
- Smart brace pair.

### Updated

- String parsing stability.
- b64 and hex bytes correctly parsed and checked.

## [0.0.5] - 2023-05-06

### Added

- Brace matching.
- Top level code completion.
- Some breadcrumbs.

### Updated

- Matches latest syntax updates.

## [0.0.4] - 2023-05-05

### Added

- Support `def` keyword.

### Updated

- Types now get colored before semantic analysis.

## [0.0.3] - 2023-05-03

### Added

- Some semantic highlighting.
- Color settings.
- Commenter.

## [0.0.2] - 2023-04-28

### Updated

- Grammar fixes.
- icon updates.

## [0.0.1] - 2023-04-27

- First alpha.



