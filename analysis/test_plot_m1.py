"""Tests for plot_m1.py:  .venv/bin/python -m unittest discover -s analysis -v"""

import math
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent))
import plot_m1  # noqa: E402

COLUMNS = ("run_id,git_sha,timestamp,variant,scenario,repetition,seed,failure_rate,threads,pool_size,transfers,"
           "completed,rejected_insufficient,rejected_fraud,failed_technical,compensated,pending_left,money_before,"
           "money_after,money_lost,inflight_mean_ms,inflight_p95_ms,inflight_max_money,latency_mean_ms,"
           "latency_p50_ms,latency_p95_ms,latency_p99_ms,duration_s,throughput_tps").split(",")


def write_run(results: Path, rows: list, *, scenario="baseline", rate=0.0, threads=1, rep=1, money_lost=0.0,
              pending_left=0, latencies=None, windows=None, tps=100.0):
    run_id = f"c-V1-{scenario}-f{rate:.2f}-t{threads}-p10-rep{rep}"
    latencies = np.arange(1, 101, dtype=float) if latencies is None else np.asarray(latencies, dtype=float)
    windows = np.arange(1, 51, dtype=float) if windows is None else np.asarray(windows, dtype=float)
    raw = results / "raw"
    raw.mkdir(parents=True, exist_ok=True)
    pd.DataFrame({"thread": 0, "sequence": range(latencies.size), "outcome": "COMPLETED",
                  "latency_ms": latencies}).to_csv(raw / f"{run_id}.csv", index=False)
    pd.DataFrame({"window_ms": windows}).to_csv(raw / f"{run_id}-windows.csv", index=False)
    pd.DataFrame({"elapsed_ms": [0, 100, 200], "total_money": [200000, 199950, 200000],
                  "inflight_money": [0, 50, 0]}).to_csv(raw / f"{run_id}-inflight.csv", index=False)
    rows.append({
        "run_id": run_id, "git_sha": "abc", "timestamp": "2026-11-15T10:00:00Z", "variant": "V1",
        "scenario": scenario, "repetition": rep, "seed": 42, "failure_rate": rate, "threads": threads,
        "pool_size": 10, "transfers": latencies.size, "completed": latencies.size, "rejected_insufficient": 0,
        "rejected_fraud": 0, "failed_technical": 0, "compensated": 0, "pending_left": pending_left,
        "money_before": 200000, "money_after": 200000 - money_lost, "money_lost": money_lost,
        "inflight_mean_ms": windows.mean(), "inflight_p95_ms": np.percentile(windows, 95),
        "inflight_max_money": 50, "latency_mean_ms": latencies.mean(), "latency_p50_ms": np.percentile(latencies, 50),
        "latency_p95_ms": np.percentile(latencies, 95), "latency_p99_ms": np.percentile(latencies, 99),
        "duration_s": 0.2, "throughput_tps": tps,
    })
    return run_id


def write_csv(results: Path, rows: list):
    pd.DataFrame(rows, columns=COLUMNS).to_csv(results / "runs.csv", index=False)


class StatisticsTest(unittest.TestCase):

    def test_mean_ci_uses_student_t(self):
        mean, half = plot_m1.mean_ci([1.0, 2.0, 3.0])
        self.assertAlmostEqual(mean, 2.0)
        self.assertAlmostEqual(half, 4.303 * 1.0 / math.sqrt(3), places=6)

    def test_single_value_has_no_interval(self):
        self.assertEqual(plot_m1.mean_ci([5.0]), (5.0, 0.0))
        self.assertTrue(math.isnan(plot_m1.mean_ci([])[0]))

    def test_t_critical(self):
        self.assertEqual(plot_m1.t_critical(2), 4.303)
        self.assertEqual(plot_m1.t_critical(14), 2.179)  # nearest tabulated df below, conservative
        self.assertEqual(plot_m1.t_critical(100), 1.96)

    def test_percentile_is_computed_on_merged_raw_values_not_averaged(self):
        rep1 = np.arange(1, 101, dtype=float)
        rep2 = np.arange(101, 201, dtype=float)

        merged = plot_m1.merged_percentile([rep1, rep2], 95)
        averaged = (np.percentile(rep1, 95) + np.percentile(rep2, 95)) / 2

        self.assertAlmostEqual(merged, 190.05)
        self.assertAlmostEqual(averaged, 145.05)
        self.assertNotAlmostEqual(merged, averaged)

    def test_merged_percentile_matches_java_statistics(self):
        # Same inputs as StatisticsTest.percentilesInterpolateLikeNumpy on the Java side.
        self.assertAlmostEqual(plot_m1.merged_percentile([[15, 20, 35, 40, 50]], 40), 29.0)
        self.assertAlmostEqual(plot_m1.merged_percentile([[15, 20, 35, 40, 50]], 95), 48.0)

    def test_bootstrap_interval_contains_the_estimate(self):
        values = np.random.default_rng(1).exponential(10, 2000)
        low, high = plot_m1.bootstrap_ci(values, lambda v: np.percentile(v, 95))
        self.assertLess(low, np.percentile(values, 95))
        self.assertGreater(high, np.percentile(values, 95))


class PipelineTest(unittest.TestCase):

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.results = Path(self.directory.name)

    def tearDown(self):
        self.directory.cleanup()

    def test_writes_every_chart_and_the_summary(self):
        rows = []
        for rate in (0.0, 0.1):
            for threads in (1, 8):
                for rep in (1, 2):
                    write_run(self.results, rows, rate=rate, threads=threads, rep=rep, tps=100.0 * threads + rep)
        write_run(self.results, rows, scenario="sampling", rate=0.1, threads=8)
        write_csv(self.results, rows)

        self.assertEqual(plot_m1.main(["--results", str(self.results)]), 0)

        plots = self.results / "plots"
        for name in ("g1_inconsistency_window", "g2_latency", "g3_throughput", "g4_money_over_time"):
            for extension in ("svg", "png"):
                self.assertTrue((plots / f"{name}.{extension}").stat().st_size > 0, f"{name}.{extension}")
        summary = (self.results / "summary.md").read_text()
        self.assertIn("passed the acceptance gate: 9", summary)
        self.assertIn("| baseline | 10% | 8 | 10 | 2 |", summary)
        self.assertIn("802 ± 6", summary)  # 801 and 802 tps: mean 801.5, t(1) x sd / sqrt(2) = 6.35
        self.assertIn("None: every run kept all the money", summary)

    def test_runs_that_break_the_gate_are_excluded_and_listed(self):
        rows = []
        write_run(self.results, rows, rate=0.0, threads=1)
        lost = write_run(self.results, rows, rate=0.1, threads=1, money_lost=25.0)
        pending = write_run(self.results, rows, rate=0.2, threads=1, pending_left=3)
        write_csv(self.results, rows)

        runs = plot_m1.load_runs(self.results)
        self.assertEqual(list(runs["gate_ok"]), [True, False, False])

        summary = plot_m1.summary_table(runs, self.results)
        self.assertNotIn("| baseline | 10% |", summary)
        self.assertIn(f"`{lost}`: money_lost=25.0", summary)
        self.assertIn(f"`{pending}`: money_lost=0.0, pending_left=3", summary)

    def test_g4_prefers_the_sampling_run(self):
        rows = []
        write_run(self.results, rows, rate=0.3, threads=16)
        sampling = write_run(self.results, rows, scenario="sampling", rate=0.1, threads=8)
        write_csv(self.results, rows)

        self.assertEqual(plot_m1.pick_g4_run(plot_m1.load_runs(self.results))["run_id"], sampling)

    def test_missing_results_is_an_error(self):
        self.assertEqual(plot_m1.main(["--results", str(self.results / "nothing")]), 1)


if __name__ == "__main__":
    unittest.main()
