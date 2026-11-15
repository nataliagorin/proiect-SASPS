package ro.saga.transfer.benchmark;

import ro.saga.transfer.common.Bank;
import ro.saga.transfer.common.TransferRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public final class WorkloadGenerator {

    private final AccountDistribution distribution;
    private final int minAmount;
    private final int maxAmount;
    private final ZipfSampler zipf;

    public WorkloadGenerator(BenchmarkProperties properties) {
        this(properties.distribution(), properties.hotAccounts(), properties.zipfExponent(),
                properties.minAmount(), properties.maxAmount());
    }

    public WorkloadGenerator(
            AccountDistribution distribution, int hotAccounts, double zipfExponent, int minAmount, int maxAmount) {
        this.distribution = distribution;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.zipf = new ZipfSampler(hotAccounts, zipfExponent);
    }

    public static long threadSeed(long seed, int repetition, int threadIndex) {
        long mixed = seed;
        mixed = mixed * 0x9E3779B97F4A7C15L + repetition;
        mixed = mixed * 0x9E3779B97F4A7C15L + threadIndex;
        return mixed;
    }

    public static int[] split(int total, int threads) {
        int[] counts = new int[threads];
        for (int i = 0; i < threads; i++) {
            counts[i] = total / threads + (i < total % threads ? 1 : 0);
        }
        return counts;
    }

    public List<List<TransferRequest>> generate(long seed, int repetition, int threads, int total, String keyPrefix) {
        int[] counts = split(total, threads);
        List<List<TransferRequest>> perThread = new ArrayList<>(threads);
        for (int thread = 0; thread < threads; thread++) {
            perThread.add(forThread(seed, repetition, thread, counts[thread], keyPrefix));
        }
        return perThread;
    }

    List<TransferRequest> forThread(long seed, int repetition, int threadIndex, int count, String keyPrefix) {
        SplittableRandom random = new SplittableRandom(threadSeed(seed, repetition, threadIndex));
        List<TransferRequest> transfers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            boolean alphaToBeta = random.nextBoolean();
            long fromAccount = pickAccount(random);
            long toAccount = pickAccount(random);
            BigDecimal amount = BigDecimal.valueOf(random.nextInt(minAmount, maxAmount + 1)).setScale(2);
            transfers.add(new TransferRequest(
                    keyPrefix + "-" + threadIndex + "-" + i,
                    alphaToBeta ? Bank.ALPHA : Bank.BETA,
                    fromAccount,
                    alphaToBeta ? Bank.BETA : Bank.ALPHA,
                    toAccount,
                    amount));
        }
        return transfers;
    }

    private long pickAccount(SplittableRandom random) {
        return switch (distribution) {
            case UNIFORM -> random.nextInt(1, BenchmarkProperties.ACCOUNTS_PER_BANK + 1);
            case ZIPF -> zipf.sample(random);
        };
    }
}
