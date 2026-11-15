package ro.saga.transfer.benchmark;

/** How the workload picks accounts. */
public enum AccountDistribution {

    /** Every account 1-100 is equally likely. */
    UNIFORM,

    /** Only the hot accounts, Zipf-weighted: account 1 is the most used. Creates contention on a few rows. */
    ZIPF
}
