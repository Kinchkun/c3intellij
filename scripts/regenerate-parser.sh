#!/bin/sh
# Regenerate src/main/gen from C3.flex and C3.bnf without the IDE (see NOTES.md, "Regenerating"):
# the lexer with JFlex 1.9.2 (through JetBrains' Grammar-Kit Gradle plugin, in a temporary
# project), the parser and PSI with the IDE's own Grammar-Kit plugin, run headless with the
# IDE's platform and this plugin's compiled classes (for psiImplUtilClass signatures).
# Then only changed files are copied (not C3StringExprImpl, which carries a hand patch) and
# patch 1 of NOTES.md is re-applied. Run `./gradlew compileJava` first (needs build/classes).
#
# CLION_PATH: the IDE (default ~/Applications/CLion.app); GRAMMAR_KIT: the Grammar-Kit plugin's
# lib folder (default: the newest under ~/Library/Application Support/JetBrains/*/plugins).
set -eu
P=$(cd "$(dirname "$0")/.." && pwd)
IDE=${CLION_PATH:-$HOME/Applications/CLion.app}/Contents
GK=${GRAMMAR_KIT:-$(ls -d "$HOME/Library/Application Support/JetBrains/"*/plugins/grammar-kit/lib 2>/dev/null | sort | tail -1)}
[ -d "$GK" ] || { echo "no Grammar-Kit plugin found (set GRAMMAR_KIT)" >&2; exit 1; }
[ -d "$P/build/classes/java/main" ] || { echo "run ./gradlew compileJava first" >&2; exit 1; }
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT

# The lexer.
cat > "$T/settings.gradle.kts" <<'KTS'
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
rootProject.name = "regen"
KTS
cat > "$T/build.gradle.kts" <<KTS
plugins { java; id("org.jetbrains.grammarkit") version "2022.3.2.2" }
repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://www.jetbrains.com/intellij-repository/releases")
}
grammarKit { jflexRelease.set("1.9.2"); intellijRelease.set("233.13135.103") }
tasks.generateLexer {
    sourceFile.set(file("$P/src/main/java/org/c3lang/intellij/C3.flex"))
    targetOutputDir.set(file("$T/lexer"))
}
KTS
"$P/gradlew" -q -p "$T" generateLexer --console=plain --no-configuration-cache
sed 's|^// source: .*/src/main/java/org/c3lang/intellij/C3.flex|// source: src/main/java/org/c3lang/intellij/C3.flex|' \
	"$T/lexer/C3Lexer.java" > "$P/src/main/gen/org/c3lang/intellij/lexer/C3Lexer.java"

# The parser and PSI: short names (b, l, r), as the IDE action generates them.
mkdir -p "$T/bnf" "$T/gen"
python3 - "$P/src/main/java/org/c3lang/intellij/C3.bnf" "$T/bnf/C3.bnf" <<'PY'
import sys
t = open(sys.argv[1]).read(); i = t.index('{')
open(sys.argv[2], 'w').write(t[:i + 1] + '\n    generate=[names="short"]' + t[i + 1:])
PY
CP="$P/build/classes/java/main:$(ls "$GK"/*.jar | tr '\n' ':')$(ls "$IDE"/lib/*.jar | tr '\n' ':')"
(cd "$P" && "$IDE/jbr/Contents/Home/bin/java" -Didea.home.path="$IDE" -Djava.awt.headless=true \
	-cp "$CP" org.intellij.grammar.Main "$T/gen" "$T/bnf/C3.bnf" > "$T/generator.log" 2>&1) \
	|| { cat "$T/generator.log" >&2; exit 1; }
cd "$T/gen"
for f in $(find . -name '*.java'); do
	case "$f" in *C3StringExprImpl.java) continue ;; esac   # (hand patch 2, NOTES.md)
	if ! cmp -s "$f" "$P/src/main/gen/$f"; then
		mkdir -p "$(dirname "$P/src/main/gen/$f")"
		cp "$f" "$P/src/main/gen/$f"
		echo "updated ${f#./}"
	fi
done

# Patch 1 (NOTES.md), and the rule name `module` (newer Grammar-Kit writes `module_$`).
python3 - "$P/src/main/gen/org/c3lang/intellij/parser/C3Parser.java" <<'PY'
import sys
p = sys.argv[1]; t = open(p).read()
t = t.replace('module_$(', 'module(').replace('"module_$"', '"module"')
if 'Precedence 2 excludes assignment/ternary' not in t:
    i = t.index('public static boolean generic_parameter(')
    old = '    r = expr(b, l + 1, -1);'
    j = t.index(old, i)
    assert j - i < 600, "generic_parameter changed: apply patch 1 by hand"
    t = t[:j] + '''    // Precedence 2 excludes assignment/ternary, so a declaration body like
    // `constdef X : inline String { CONTENT_TYPE = "..." }` is not misparsed as
    // generic arguments `String{...}`. See generic_parameter in C3.bnf.
    r = expr(b, l + 1, 2);''' + t[j + len(old):]
open(p, 'w').write(t)
PY
echo "regenerated (lexer, parser, PSI); now ./gradlew test"
