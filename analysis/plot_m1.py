#!/usr/bin/env python3
"""Charts G1-G4 and the results table for the M1 presentation, from the benchmark output in results/.

    .venv/bin/python analysis/plot_m1.py [--results results] [--out results/plots] [--threads 8]

Reads results/runs.csv (one row per run) and results/raw/ (every transfer's latency, every inconsistency window,
the money-in-flight samples). Runs that broke the acceptance gate (money lost or transfers left PENDING) stay in the
CSV but are left out of the charts, as docs/experiments.md requires.

Statistics:
  * means across repetitions: mean +/- 95% confidence interval (Student t);
  * percentiles across repetitions: computed on the merged raw values of all repetitions, never by averaging the
    per-run percentiles, with a 95% bootstrap interval over the transfers.

Writes G1-G4 as SVG and PNG (light theme, for slides) and summary.md (the table view of the same numbers).
"""

from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

import matplotlib
import matplotlib.ticker

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402

# Validated with the dataviz palette checker (light surface): the ordinal ramp is one hue, monotone in lightness,
# its light end clears 2:1; the categorical slots pass the CVD and normal-vision gates (contrast relief: summary.md).
SURFACE = "#fcfcfb"
TEXT = "#0b0b0b"
TEXT_SECONDARY = "#52514e"
GRID = "#e4e3df"
ORDINAL_RAMP = ["#86b6ef", "#3987e5", "#256abf", "#184f95", "#0d366b"]
CATEGORICAL = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100"]

BOOTSTRAP_SAMPLES = 200
BOOTSTRAP_SEED = 42

# Two-sided 95% Student t critical values by degrees of freedom.
_T95 = {1: 12.706, 2: 4.303, 3: 3.182, 4: 2.776, 5: 2.571, 6: 2.447, 7: 2.365, 8: 2.306, 9: 2.262, 10: 2.228,
        12: 2.179, 15: 2.131, 20: 2.086, 25: 2.060, 30: 2.042}


# --- Statistics ----------------------------------------------------------------------------------------------------

def t_critical(df: int) -> float:
    if df < 1:
        return math.nan
    if df > 30:
        return 1.96
    return _T95[max(k for k in _T95 if k <= df)]


def mean_ci(values) -> tuple[float, float]:
    """Mean and half-width of its 95% confidence interval; the half-width is 0 for a single value."""
    values = np.asarray([v for v in values if not pd.isna(v)], dtype=float)
    if values.size == 0:
        return math.nan, math.nan
    mean = float(values.mean())
    if values.size < 2:
        return mean, 0.0
    return mean, t_critical(values.size - 1) * float(values.std(ddof=1)) / math.sqrt(values.size)


def merged_percentile(groups, q: float) -> float:
    """Percentile q (0-100) over the union of all groups, numpy's default linear interpolation."""
    arrays = [np.asarray(g, dtype=float) for g in groups if len(g)]
    if not arrays:
        return math.nan
    return float(np.percentile(np.concatenate(arrays), q))


def bootstrap_ci(values, statistic, samples: int = BOOTSTRAP_SAMPLES) -> tuple[float, float]:
    values = np.asarray(values, dtype=float)
    if values.size < 2:
        return math.nan, math.nan
    rng = np.random.default_rng(BOOTSTRAP_SEED)
    estimates = [statistic(rng.choice(values, size=values.size, replace=True)) for _ in range(samples)]
    low, high = np.percentile(estimates, [2.5, 97.5])
    return float(low), float(high)


# --- Data ----------------------------------------------------------------------------------------------------------

def load_runs(results: Path) -> pd.DataFrame:
    runs = pd.read_csv(results / "runs.csv")
    runs["gate_ok"] = (runs["money_lost"] == 0) & (runs["pending_left"] == 0)
    return runs


def raw_latencies(results: Path, run_id: str) -> np.ndarray:
    return pd.read_csv(results / "raw" / f"{run_id}.csv")["latency_ms"].to_numpy()


def raw_windows(results: Path, run_id: str) -> np.ndarray | None:
    path = results / "raw" / f"{run_id}-windows.csv"
    return pd.read_csv(path)["window_ms"].to_numpy() if path.exists() else None


def raw_inflight(results: Path, run_id: str) -> pd.DataFrame:
    return pd.read_csv(results / "raw" / f"{run_id}-inflight.csv")


def ordinal_colors(n: int) -> list[str]:
    if n <= 1:
        return [ORDINAL_RAMP[2]]
    indices = np.linspace(0, len(ORDINAL_RAMP) - 1, n).round().astype(int)
    return [ORDINAL_RAMP[i] for i in indices]


def percent(rate: float) -> str:
    return f"{rate * 100:g}%"


# --- Chart style ---------------------------------------------------------------------------------------------------

def apply_style() -> None:
    plt.rcParams.update({
        "figure.facecolor": SURFACE,
        "axes.facecolor": SURFACE,
        "savefig.facecolor": SURFACE,
        "font.size": 10,
        "axes.titlesize": 11,
        "axes.titleweight": "bold",
        "axes.titlelocation": "left",
        "axes.labelcolor": TEXT_SECONDARY,
        "axes.edgecolor": GRID,
        "axes.spines.top": False,
        "axes.spines.right": False,
        "axes.grid": True,
        "axes.grid.axis": "y",
        "grid.color": GRID,
        "grid.linewidth": 0.8,
        "xtick.color": TEXT_SECONDARY,
        "ytick.color": TEXT_SECONDARY,
        "text.color": TEXT,
        "legend.frameon": False,
        "lines.linewidth": 2,
        "lines.markersize": 6,
        "svg.fonttype": "none",
    })


def save(fig, out: Path, name: str) -> list[Path]:
    out.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    paths = [out / f"{name}.svg", out / f"{name}.png"]
    fig.savefig(paths[0])
    fig.savefig(paths[1], dpi=200)
    plt.close(fig)
    return paths


# --- G1: inconsistency window vs failure rate ----------------------------------------------------------------------

def plot_g1(runs: pd.DataFrame, results: Path, out: Path) -> list[Path]:
    baseline = runs[(runs["scenario"] == "baseline") & runs["gate_ok"]]
    if baseline.empty:
        print("G1 skipped: no baseline runs passed the gate")
        return []
    threads = sorted(baseline["threads"].unique())
    fig, (ax_mean, ax_p95) = plt.subplots(1, 2, figsize=(10, 4))
    for color, count in zip(ordinal_colors(len(threads)), threads):
        subset = baseline[baseline["threads"] == count]
        rates, means, mean_err, p95s, p95_err = [], [], [], [], []
        for rate, group in subset.groupby("failure_rate"):
            mean, half = mean_ci(group["inflight_mean_ms"])
            windows = [raw_windows(results, run_id) for run_id in group["run_id"]]
            if all(w is not None for w in windows):
                merged = np.concatenate([w for w in windows if len(w)]) if any(len(w) for w in windows) else []
                p95 = merged_percentile([merged], 95)
                low, high = bootstrap_ci(merged, lambda v: np.percentile(v, 95))
                err = (p95 - low, high - p95) if not math.isnan(low) else (0, 0)
            else:  # older results without raw windows: mean of the per-run p95
                p95, half95 = mean_ci(group["inflight_p95_ms"])
                err = (half95, half95)
            rates.append(rate * 100)
            means.append(mean)
            mean_err.append(half)
            p95s.append(p95)
            p95_err.append(err)
        label = f"{count} thread" + ("s" if count != 1 else "")
        ax_mean.errorbar(rates, means, yerr=mean_err, color=color, marker="o", capsize=3, label=label)
        ax_p95.errorbar(rates, p95s, yerr=np.array(p95_err).T, color=color, marker="o", capsize=3, label=label)
    ax_mean.set_title("G1 · Inconsistency window, mean")
    ax_p95.set_title("G1 · Inconsistency window, p95")
    for ax in (ax_mean, ax_p95):
        ax.set_xlabel("Injected failure rate (%)")
        ax.set_ylabel("finalized_at − created_at (ms)")
        ax.set_ylim(bottom=0)
    ax_p95.legend(title="Client threads", loc="upper left")
    return save(fig, out, "g1_inconsistency_window")


# --- G2: latency -----------------------------------------------------------------------------------------------------

def plot_g2(runs: pd.DataFrame, results: Path, out: Path, threads: int) -> list[Path]:
    baseline = runs[(runs["scenario"] == "baseline") & runs["gate_ok"]]
    if baseline.empty:
        print("G2 skipped: no baseline runs passed the gate")
        return []
    available = sorted(baseline["threads"].unique())
    if threads not in available:
        threads = available[-1]
    subset = baseline[baseline["threads"] == threads]
    rates = sorted(subset["failure_rate"].unique())
    latencies = {rate: np.concatenate([raw_latencies(results, r)
                                       for r in subset[subset["failure_rate"] == rate]["run_id"]])
                 for rate in rates}

    stats = [("mean", np.mean), ("p50", lambda v: np.percentile(v, 50)),
             ("p95", lambda v: np.percentile(v, 95)), ("p99", lambda v: np.percentile(v, 99))]
    fig, (ax_bars, ax_ecdf) = plt.subplots(1, 2, figsize=(11, 4.2), gridspec_kw={"width_ratios": [3, 2]})
    width = 0.8 / len(stats)
    x = np.arange(len(rates))
    for i, ((name, fn), color) in enumerate(zip(stats, CATEGORICAL)):
        values = [fn(latencies[rate]) for rate in rates]
        intervals = [bootstrap_ci(latencies[rate], fn) for rate in rates]
        yerr = np.array([[v - lo, hi - v] if not math.isnan(lo) else [0, 0]
                         for v, (lo, hi) in zip(values, intervals)]).T
        ax_bars.bar(x + (i - (len(stats) - 1) / 2) * width, values, width, yerr=yerr, capsize=2, color=color,
                    edgecolor=SURFACE, linewidth=1, label=name, error_kw={"ecolor": TEXT_SECONDARY, "lw": 1})
    ax_bars.set_xticks(x, [percent(rate) for rate in rates])
    ax_bars.set_xlabel("Injected failure rate")
    ax_bars.set_ylabel("Latency per transfer (ms)")
    ax_bars.set_title(f"G2 · Latency, {threads} client threads")
    ax_bars.legend(ncols=4, loc="upper left")

    for color, rate in zip(ordinal_colors(len(rates)), rates):
        values = np.sort(latencies[rate])
        ax_ecdf.step(values, np.arange(1, values.size + 1) / values.size, where="post", color=color,
                     label=percent(rate))
    ax_ecdf.set_xscale("log")
    ax_ecdf.set_xlabel("Latency per transfer (ms, log scale)")
    ax_ecdf.set_ylabel("Share of transfers at or below")
    ax_ecdf.set_title("G2 · Latency distribution (ECDF)")
    ax_ecdf.grid(True, axis="both")
    ax_ecdf.legend(title="Failure rate", loc="lower right")
    return save(fig, out, "g2_latency")


# --- G3: throughput vs threads -------------------------------------------------------------------------------------

def plot_g3(runs: pd.DataFrame, out: Path) -> list[Path]:
    baseline = runs[(runs["scenario"] == "baseline") & runs["gate_ok"]]
    if baseline.empty:
        print("G3 skipped: no baseline runs passed the gate")
        return []
    rates = sorted(baseline["failure_rate"].unique())
    fig, ax = plt.subplots(figsize=(7, 4.2))
    for color, rate in zip(ordinal_colors(len(rates)), rates):
        subset = baseline[baseline["failure_rate"] == rate]
        points = [(threads, *mean_ci(group["throughput_tps"])) for threads, group in subset.groupby("threads")]
        threads, means, halves = zip(*points)
        ax.errorbar(threads, means, yerr=halves, color=color, marker="o", capsize=3, label=percent(rate))
    ax.set_xscale("log", base=2)
    thread_values = sorted(baseline["threads"].unique())
    ax.set_xticks(thread_values, [str(t) for t in thread_values])
    ax.minorticks_off()
    ax.set_ylim(bottom=0)
    ax.set_xlabel("Client threads")
    ax.set_ylabel("Throughput (transfers/s)")
    ax.set_title("G3 · Throughput vs client threads")
    ax.legend(title="Failure rate", loc="upper left")
    return save(fig, out, "g3_throughput")


# --- G4: money over time -------------------------------------------------------------------------------------------

def pick_g4_run(runs: pd.DataFrame) -> pd.Series | None:
    ok = runs[runs["gate_ok"]]
    sampling = ok[ok["scenario"] == "sampling"]
    if not sampling.empty:
        return sampling.iloc[0]
    baseline = ok[ok["scenario"] == "baseline"]
    if baseline.empty:
        return None
    return baseline.sort_values(["failure_rate", "threads"], ascending=False).iloc[0]


def plot_g4(runs: pd.DataFrame, results: Path, out: Path) -> list[Path]:
    run = pick_g4_run(runs)
    if run is None:
        print("G4 skipped: no run passed the gate")
        return []
    samples = raw_inflight(results, run["run_id"])
    seconds = samples["elapsed_ms"] / 1000
    load_end = float(run["duration_s"])
    # Two panels on a shared time axis, not one chart with two y-scales.
    fig, (ax_total, ax_inflight) = plt.subplots(2, 1, figsize=(9, 5), sharex=True)
    ax_total.step(seconds, samples["total_money"], where="post", color=ORDINAL_RAMP[3])
    ax_total.axhline(200000, color=TEXT_SECONDARY, linewidth=1, linestyle="--")
    low = float(samples["total_money"].min())
    ax_total.set_ylim(low - (200000 - low) * 0.05 - 1, 200000 + (200000 - low) * 0.18 + 1)
    ax_total.annotate("expected 200,000", (float(seconds.min()), 200000), xytext=(2, 4),
                      textcoords="offset points", color=TEXT_SECONDARY, fontsize=9)
    ax_total.set_ylabel("Total money (lei)")
    ax_total.set_title(f"G4 · Money over time — {percent(run['failure_rate'])} failures, {run['threads']} threads",
                       pad=12)
    ax_total.yaxis.set_major_formatter(matplotlib.ticker.StrMethodFormatter("{x:,.0f}"))

    ax_inflight.fill_between(seconds, samples["inflight_money"], step="post", color=ORDINAL_RAMP[1], alpha=0.35,
                             linewidth=0)
    ax_inflight.step(seconds, samples["inflight_money"], where="post", color=ORDINAL_RAMP[2])
    ax_inflight.set_ylabel("Money in flight (lei)")
    ax_inflight.set_xlabel("Time since the measured load started (s)")
    ax_inflight.set_ylim(bottom=0)
    for ax in (ax_total, ax_inflight):
        ax.axvline(load_end, color=TEXT_SECONDARY, linewidth=1, linestyle=":")
    ax_inflight.annotate("load ends", (load_end, ax_inflight.get_ylim()[1]), xytext=(4, -12),
                         textcoords="offset points", color=TEXT_SECONDARY, fontsize=9)
    return save(fig, out, "g4_money_over_time")


# --- Table view ----------------------------------------------------------------------------------------------------

def summary_table(runs: pd.DataFrame, results: Path) -> str:
    lines = [
        "# M1 benchmark results",
        "",
        f"Runs: {len(runs)} · passed the acceptance gate: {int(runs['gate_ok'].sum())} · "
        f"git: {', '.join(sorted(runs['git_sha'].astype(str).unique()))}",
        "",
        "Latency and window percentiles are computed on the merged raw values of all repetitions; \"latency p95 per "
        "run\" is the mean ± standard deviation of the per-run p95, reported separately as docs/experiments.md asks. "
        "Throughput is the mean ± 95% CI across repetitions.",
        "",
        "| scenario | failure | threads | pool | reps | completed | compensated | technical | window mean ms "
        "| window p95 ms | latency p50 ms | latency p95 ms | latency p95 per run ms | latency p99 ms | throughput tps |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    ok = runs[runs["gate_ok"]]
    keys = ["scenario", "failure_rate", "threads", "pool_size"]
    for (scenario, rate, threads, pool), group in ok.groupby(keys, sort=False):
        latencies = [raw_latencies(results, r) for r in group["run_id"]]
        windows = [raw_windows(results, r) for r in group["run_id"]]
        window_p95 = (merged_percentile(windows, 95) if all(w is not None for w in windows)
                      else group["inflight_p95_ms"].mean())
        transfers = group["transfers"].sum()
        throughput, half = mean_ci(group["throughput_tps"])
        p95_runs = group["latency_p95_ms"]
        p95_sd = p95_runs.std(ddof=1) if len(p95_runs) > 1 else 0.0
        lines.append(
            f"| {scenario} | {percent(rate)} | {threads} | {pool} | {len(group)} "
            f"| {group['completed'].sum() / transfers:.1%} | {group['compensated'].sum() / transfers:.1%} "
            f"| {int(group['failed_technical'].sum())} | {group['inflight_mean_ms'].mean():.1f} | {window_p95:.1f} "
            f"| {merged_percentile(latencies, 50):.1f} | {merged_percentile(latencies, 95):.1f} "
            f"| {p95_runs.mean():.1f} ± {p95_sd:.1f} | {merged_percentile(latencies, 99):.1f} "
            f"| {throughput:.0f} ± {half:.0f} |")
    failed = runs[~runs["gate_ok"]]
    lines += ["", "## Runs excluded by the acceptance gate", ""]
    if failed.empty:
        lines.append("None: every run kept all the money and left nothing PENDING.")
    else:
        lines += [f"- `{r.run_id}`: money_lost={r.money_lost}, pending_left={r.pending_left}"
                  for r in failed.itertuples()]
    return "\n".join(lines) + "\n"


# --- Main ----------------------------------------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--results", type=Path, default=Path("results"), help="benchmark output directory")
    parser.add_argument("--out", type=Path, default=None, help="where the charts go (default: <results>/plots)")
    parser.add_argument("--threads", type=int, default=8, help="client threads shown in G2 (default 8)")
    args = parser.parse_args(argv)

    if not (args.results / "runs.csv").exists():
        print(f"{args.results / 'runs.csv'} not found; run scripts/run-m1.sh first", file=sys.stderr)
        return 1
    out = args.out or args.results / "plots"
    apply_style()
    runs = load_runs(args.results)

    written = []
    written += plot_g1(runs, args.results, out)
    written += plot_g2(runs, args.results, out, args.threads)
    written += plot_g3(runs, out)
    written += plot_g4(runs, args.results, out)
    summary = args.results / "summary.md"
    summary.write_text(summary_table(runs, args.results), encoding="utf-8")
    written.append(summary)

    for path in written:
        print(f"wrote {path}")
    excluded = int((~runs["gate_ok"]).sum())
    if excluded:
        print(f"{excluded} runs broke the acceptance gate and are not in the charts (see {summary})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
