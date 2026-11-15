package ro.saga.transfer.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class EnvironmentReport {

    private EnvironmentReport() {
    }

    public static Map<String, Object> collect(BenchmarkProperties properties) {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("date", Instant.now().toString());
        values.put("git_sha", properties.gitSha());
        values.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        values.put("arch", System.getProperty("os.arch"));
        values.put("cpu", cpuModel());
        values.put("cores", runtime.availableProcessors());
        values.put("ram_bytes", totalMemory());
        values.put("jvm", System.getProperty("java.vm.name") + " " + Runtime.version());
        values.put("jvm_vendor", System.getProperty("java.vm.vendor"));
        values.put("jvm_flags", ManagementFactory.getRuntimeMXBean().getInputArguments());
        values.put("max_heap_bytes", runtime.maxMemory());
        values.put("docker", properties.dockerVersion());
        return values;
    }

    public static void write(Path file, Map<String, Object> values) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, toJson(values) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    static String toJson(Map<String, Object> values) {
        return values.entrySet().stream()
                .map(entry -> "  " + quote(entry.getKey()) + ": " + value(entry.getValue()))
                .collect(Collectors.joining(",\n", "{\n", "\n}"));
    }

    private static String value(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(EnvironmentReport::value).collect(Collectors.joining(", ", "[", "]"));
        }
        return value == null ? "null" : quote(value.toString());
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String cpuModel() {
        Path cpuInfo = Path.of("/proc/cpuinfo");
        if (Files.isReadable(cpuInfo)) {
            try (var lines = Files.lines(cpuInfo)) {
                return lines.filter(line -> line.startsWith("model name"))
                        .map(line -> line.substring(line.indexOf(':') + 1).trim())
                        .findFirst()
                        .orElse(System.getProperty("os.arch"));
            } catch (IOException | UncheckedIOException e) {
                return System.getProperty("os.arch");
            }
        }
        return System.getProperty("os.arch");
    }

    private static long totalMemory() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            return os.getTotalMemorySize();
        }
        return -1;
    }
}
