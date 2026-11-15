package ro.saga.transfer.benchmark;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("benchmark")
public class BenchmarkRunner implements CommandLineRunner {

    private final BenchmarkCampaign campaign;
    private final ConfigurableApplicationContext context;

    public BenchmarkRunner(BenchmarkCampaign campaign, ConfigurableApplicationContext context) {
        this.campaign = campaign;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        BenchmarkCampaign.Summary summary = campaign.run();
        int exitCode = summary.failedRuns() > 0 ? 1 : 0;
        // The scheduler threads (RecoveryJob) keep the JVM alive, so the exit is explicit.
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }
}
