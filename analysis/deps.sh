#!/usr/bin/env bash
# G10: dependency graphs of our own classes, from the compiled code (jdeps from the JDK + Graphviz).
#
#   analysis/deps.sh                 package graph of the whole app + class graph of V1 (package plain)
#   analysis/deps.sh orchestration   ... + class graph of another variant package (M2: orchestration, choreography, ...)
#
# Writes analysis/graphs/packages.{dot,svg} and analysis/graphs/<name>-classes.{dot,svg}, where <name> is v1 for
# plain, v2 for orchestration, v3 for choreography, v4 for twophase, otherwise the package name. Every variant's
# graph is made the same way, so V1 can be put next to V2/V3/V5 on a slide.
#
# jdeps sees only code dependencies (fields, parameters, calls). Spring injection shows up as a dependency on the
# interface, which is what the comparison needs. Requires Graphviz: sudo apt install graphviz
set -euo pipefail

cd "$(dirname "$0")/.."

VARIANTS=("${@:-plain}")
OUT=analysis/graphs
CLASSES=target/classes
JDEPS="${JAVA_HOME:+$JAVA_HOME/bin/}jdeps"

command -v dot >/dev/null || { echo "Graphviz is missing: sudo apt install graphviz" >&2; exit 1; }
command -v "$JDEPS" >/dev/null || { echo "jdeps not found; set JAVA_HOME to JDK 25" >&2; exit 1; }

if [[ ! -d "$CLASSES/ro/saga/transfer" ]]; then
    echo "Compiling the application"
    ./mvnw -B -q -DskipTests compile
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

# style <jdeps dot> <highlighted prefix, after shortening> <output dot>
# Drops the " (classes)" suffix jdeps adds, shortens ro.saga.transfer.x to x, folds inner classes (A$1) into their
# outer class and applies the chart palette. With a prefix (a variant's class graph), it keeps only the variant's
# classes, what they use directly, and the links among those, and highlights the variant's classes.
style() {
    local input="$1" highlight="$2" output="$3"
    sed -e 's/ (classes)//g' -e 's/ro\.saga\.transfer\.//g' "$input" | awk -v highlight="$highlight" '
        function clean(name) { gsub(/[";]/, "", name); sub(/\$.*/, "", name); return name }
        NR == 1 { header = $0; next }
        /->/ {
            src = clean($1); dst = clean($3)
            if (src != dst && !((src, dst) in seen)) { seen[src, dst] = 1; from[++n] = src; to[n] = dst }
            next
        }
        END {
            print header
            print "    graph [rankdir=LR, fontname=\"Helvetica\", bgcolor=\"#fcfcfb\", nodesep=0.25, ranksep=0.7];"
            print "    node  [shape=box, style=\"rounded,filled\", fontname=\"Helvetica\", fontsize=11,"
            print "           color=\"#86b6ef\", fillcolor=\"#cde2fb\", fontcolor=\"#0b0b0b\"];"
            print "    edge  [color=\"#52514e\", arrowsize=0.6];"
            for (i = 1; i <= n; i++) {
                if (highlight != "" && index(from[i], highlight) == 1) { own[from[i]] = 1; used[from[i]] = 1; used[to[i]] = 1 }
            }
            for (i = 1; i <= n; i++) {
                if (highlight == "" || (from[i] in own) || ((from[i] in used) && (to[i] in used))) {
                    printf "    \"%s\" -> \"%s\";\n", from[i], to[i]
                }
            }
            for (name in own) {
                printf "    \"%s\" [fillcolor=\"#2a78d6\", color=\"#256abf\", fontcolor=\"#ffffff\"];\n", name
            }
            print "}"
        }' > "$output"
}

graph_name() {
    case "$1" in
        plain) echo v1 ;;
        orchestration) echo v2 ;;
        choreography) echo v3 ;;
        twophase) echo v4 ;;
        *) echo "$1" ;;
    esac
}

echo "Package graph of the whole application"
"$JDEPS" -verbose:package \
    -include 'ro\.saga\.transfer\..*' -e 'ro\.saga\.transfer\..*' \
    -dotoutput "$TMP/packages" "$CLASSES"
style "$TMP/packages/classes.dot" "" "$OUT/packages.dot"
dot -Tsvg "$OUT/packages.dot" -o "$OUT/packages.svg"
echo "  $OUT/packages.svg"

for variant in "${VARIANTS[@]}"; do
    if [[ ! -d "$CLASSES/ro/saga/transfer/$variant" ]] || ! ls "$CLASSES/ro/saga/transfer/$variant"/*.class >/dev/null 2>&1; then
        echo "No compiled classes in package $variant; skipped" >&2
        continue
    fi
    name=$(graph_name "$variant")
    echo "Class graph of $variant ($name) and what it uses from bank and common"
    # -filter:none keeps dependencies inside a package, otherwise jdeps hides them and the variant looks empty.
    "$JDEPS" -verbose:class -filter:none \
        -include "ro\\.saga\\.transfer\\.($variant|bank|common)\\..*" \
        -e 'ro\.saga\.transfer\..*' \
        -dotoutput "$TMP/$name" "$CLASSES"
    style "$TMP/$name/classes.dot" "$variant." "$OUT/$name-classes.dot"
    dot -Tsvg "$OUT/$name-classes.dot" -o "$OUT/$name-classes.svg"
    echo "  $OUT/$name-classes.svg"
done
