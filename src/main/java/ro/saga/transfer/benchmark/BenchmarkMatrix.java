package ro.saga.transfer.benchmark;

import java.util.ArrayList;
import java.util.List;

public final class BenchmarkMatrix {

    private BenchmarkMatrix() {
    }

    public static List<RunConfig> expand(BenchmarkProperties properties, int poolSize) {
        List<RunConfig> runs = new ArrayList<>();
        for (String variant : properties.variants()) {
            for (double rate : properties.failureRates()) {
                for (int threads : properties.threads()) {
                    for (int repetition = 1; repetition <= properties.repetitions(); repetition++) {
                        runs.add(new RunConfig(variant, properties.scenario(), rate, threads, repetition,
                                properties.seed(), poolSize));
                    }
                }
            }
        }
        return List.copyOf(runs);
    }
}
