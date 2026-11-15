#!/usr/bin/env bash
# Runs the M1 measurement campaign (docs/experiments.md, "Ce rulăm în M1") and writes everything to results/.
#
#   scripts/run-m1.sh                    full campaign, about an hour
#   scripts/run-m1.sh --quick            1000 transfers, 1 repetition: checks the pipeline in a few minutes
#   scripts/run-m1.sh --scenarios "baseline sampling"   only some scenarios
#   scripts/run-m1.sh --skip-build       reuse target/saga-transfer-*.jar
#   scripts/run-m1.sh --skip-plots       no charts (no Python needed)
#
# Scenarios: baseline, fraud, pool, contention, sampling. Each runs in its own JVM; the pool size can only be set
# at startup, so the pool scenario starts one JVM per size. A previous results/ is moved to results.<timestamp>/.
# Exits 1 if any run broke the acceptance gate (money lost or transfers left PENDING); the other scenarios still run.
#
# Measure on a laptop on mains power, with other applications closed.
set -euo pipefail

cd "$(dirname "$0")/.."

ALL_SCENARIOS="baseline fraud pool contention sampling"
SCENARIOS="$ALL_SCENARIOS"
QUICK=false
SKIP_BUILD=false
SKIP_PLOTS=false
RESULTS_DIR=results
HEAP="${BENCHMARK_HEAP:-2g}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --quick) QUICK=true ;;
        --skip-build) SKIP_BUILD=true ;;
        --scenarios) SCENARIOS="$2"; shift ;;
        --skip-plots) SKIP_PLOTS=true ;;
        -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
        *) echo "Unknown option: $1 (see --help)" >&2; exit 2 ;;
    esac
    shift
done

for scenario in $SCENARIOS; do
    [[ " $ALL_SCENARIOS " == *" $scenario "* ]] || { echo "Unknown scenario: $scenario" >&2; exit 2; }
done

log() { printf '\n\033[1m== %s\033[0m\n' "$*"; }

# --- Environment ---------------------------------------------------------------------------------------------------

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVA_MAJOR=$("$JAVA" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}')
if [[ "${JAVA_MAJOR:-0}" -lt 25 ]]; then
    echo "JDK 25 is required, found ${JAVA_MAJOR:-none} ($JAVA). Set JAVA_HOME, e.g." >&2
    echo "  export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64" >&2
    exit 1
fi

GIT_SHA=$(git rev-parse --short HEAD)
if ! git diff --quiet HEAD -- src pom.xml; then
    GIT_SHA="$GIT_SHA-dirty"
fi
DOCKER_VERSION=$(docker version --format '{{.Server.Version}}' 2>/dev/null || echo unknown)

if $QUICK; then
    TRANSFERS=1000; WARMUP=100; REPETITIONS=1
else
    TRANSFERS=10000; WARMUP=200; REPETITIONS=3
fi

# --- Databases and build -------------------------------------------------------------------------------------------

log "Starting the bank databases"
docker compose up -d --wait

if ! $SKIP_BUILD; then
    log "Building the application (tests run in the regular build)"
    ./mvnw -B -q -DskipTests package
fi
JAR=$(ls target/saga-transfer-*.jar | grep -v plain | head -n 1)

if [[ -d "$RESULTS_DIR" ]]; then
    ARCHIVE="$RESULTS_DIR.$(date +%Y%m%d-%H%M%S)"
    log "Moving the previous $RESULTS_DIR/ to $ARCHIVE/"
    mv "$RESULTS_DIR" "$ARCHIVE"
fi
mkdir -p "$RESULTS_DIR/logs"

# --- Runs ----------------------------------------------------------------------------------------------------------

FAILED=0

# benchmark <log-name> <extra Spring arguments...>; REPS=<n> overrides the repetitions for one scenario.
# Never repeat an --app.benchmark.* argument: Spring would merge both values into a list.
benchmark() {
    local name="$1"; shift
    log "Scenario $name"
    set +e
    "$JAVA" "-Xms$HEAP" "-Xmx$HEAP" -XX:+AlwaysPreTouch -jar "$JAR" \
        --spring.profiles.active=benchmark \
        --app.benchmark.transfers="$TRANSFERS" \
        --app.benchmark.warmup-transfers="$WARMUP" \
        --app.benchmark.repetitions="${REPS:-$REPETITIONS}" \
        --app.benchmark.output-dir="$RESULTS_DIR" \
        --app.benchmark.git-sha="$GIT_SHA" \
        --app.benchmark.docker-version="$DOCKER_VERSION" \
        "$@" 2>&1 | tee "$RESULTS_DIR/logs/$name.log" | grep --line-buffered -E 'BenchmarkCampaign|Exception'
    local codes=("${PIPESTATUS[@]}")
    set -e
    # Only the JVM's status counts; grep exits 1 when it shows nothing.
    local status=${codes[0]}
    if [[ "$status" -ne 0 ]]; then
        echo "Scenario $name failed (exit $status); see $RESULTS_DIR/logs/$name.log" >&2
        FAILED=1
    fi
}

for scenario in $SCENARIOS; do
    case "$scenario" in
        baseline)
            # G1-G3: every failure rate x 1, 2, 4, 8, 16 threads.
            benchmark baseline --app.benchmark.scenario=baseline \
                --app.benchmark.failure-rates=0.0,0.05,0.1,0.2,0.3 --app.benchmark.threads=1,2,4,8,16
            ;;
        fraud)
            # ~5% fraud rejections: 5 of the 100 source accounts are blocked.
            benchmark fraud --app.benchmark.scenario=fraud --app.fraud.blocked-accounts=1,2,3,4,5 \
                --app.benchmark.failure-rates=0.0,0.1 --app.benchmark.threads=8
            ;;
        pool)
            for size in 5 10 20; do
                benchmark "pool-$size" --app.benchmark.scenario=pool \
                    --app.datasource.alpha-bank.hikari.maximum-pool-size="$size" \
                    --app.datasource.beta-bank.hikari.maximum-pool-size="$size" \
                    --app.benchmark.failure-rates=0.0 --app.benchmark.threads=8,16
            done
            ;;
        contention)
            # 10 hot accounts, Zipf-weighted.
            benchmark contention --app.benchmark.scenario=contention \
                --app.benchmark.distribution=zipf --app.benchmark.hot-accounts=10 \
                --app.benchmark.failure-rates=0.0,0.1 --app.benchmark.threads=8,16
            ;;
        sampling)
            # G4: one run with denser money-in-flight sampling.
            REPS=1 benchmark sampling --app.benchmark.scenario=sampling \
                --app.benchmark.sample-interval-ms=50 --app.benchmark.failure-rates=0.1 --app.benchmark.threads=8
            ;;
    esac
done

# --- Charts --------------------------------------------------------------------------------------------------------

if ! $SKIP_PLOTS && [[ -f "$RESULTS_DIR/runs.csv" ]]; then
    log "Charts G1-G4 and summary.md"
    if [[ ! -x .venv/bin/python ]]; then
        python3 -m venv .venv
    fi
    .venv/bin/pip install -q -r analysis/requirements.txt
    .venv/bin/python analysis/plot_m1.py --results "$RESULTS_DIR" || FAILED=1
fi

log "Results"
echo "CSV:          $RESULTS_DIR/runs.csv ($(($(wc -l < "$RESULTS_DIR/runs.csv") - 1)) runs)"
echo "Raw data:     $RESULTS_DIR/raw/"
echo "Environment:  $RESULTS_DIR/environment.json"
$SKIP_PLOTS || echo "Charts:       $RESULTS_DIR/plots/ (table view: $RESULTS_DIR/summary.md)"

if [[ "$FAILED" -ne 0 ]]; then
    echo "At least one run broke the acceptance gate or failed; check the logs above." >&2
    exit 1
fi
