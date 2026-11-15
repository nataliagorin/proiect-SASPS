package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentReportTest {

    @TempDir
    Path directory;

    @Test
    void collectsMachineJvmAndRunDetails() {
        Map<String, Object> values = EnvironmentReport.collect(new TestProperties().build(), 10);

        assertThat(values).containsKeys("date", "git_sha", "os", "cpu", "cores", "ram_bytes", "jvm", "jvm_flags",
                "max_heap_bytes", "docker", "pool_size", "scenario", "transfers", "seed");
        assertThat(values.get("git_sha")).isEqualTo("test-sha");
        assertThat(values.get("docker")).isEqualTo("test-docker");
        assertThat((Integer) values.get("cores")).isPositive();
        assertThat(values.get("jvm").toString()).contains(Integer.toString(Runtime.version().feature()));
    }

    @Test
    void writesValidJson() throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("text", "quote \" backslash \\ newline \n");
        values.put("number", 42);
        values.put("list", List.of("-Xmx2g", "-Xms2g"));
        values.put("missing", null);
        Path file = directory.resolve("out/environment.json");

        EnvironmentReport.write(file, values);

        assertThat(Files.readString(file)).isEqualTo("""
                {
                  "text": "quote \\" backslash \\\\ newline \\n",
                  "number": 42,
                  "list": ["-Xmx2g", "-Xms2g"],
                  "missing": null
                }
                """);
    }
}
