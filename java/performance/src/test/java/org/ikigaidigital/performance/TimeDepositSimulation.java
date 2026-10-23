package org.ikigaidigital.performance;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Simulation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class TimeDepositSimulation extends Simulation {
    private final int deposits = Integer.getInteger("perf.deposits", 100000);
    private final int pageSize = Integer.getInteger("perf.pageSize", 20);
    private final int seconds = Integer.getInteger("perf.seconds", 60);
    private final int levelSeconds = Integer.getInteger("perf.levelSeconds", 30);
    private final int rampSeconds = Integer.getInteger("perf.rampSeconds", 10);
    private final double rate = Double.parseDouble(System.getProperty("perf.rate", "50"));
    private final double step = Double.parseDouble(System.getProperty("perf.step", "75"));
    private final int levels = Integer.getInteger("perf.levels", 3);
    private final int contention = Integer.getInteger("perf.contention", 20);
    private final double postRate = Double.parseDouble(System.getProperty("perf.postRate", "0.1"));
    private final LongAdder accepted = new LongAdder();
    private final LongAdder busy = new LongAdder();
    private final Set<String> acceptedIds = ConcurrentHashMap.newKeySet();
    private final Set<String> busyIds = ConcurrentHashMap.newKeySet();

    public TimeDepositSimulation() {
        if (deposits < 1 || pageSize < 1 || seconds < 1 || levels < 1 || levelSeconds < 1
                || rampSeconds < 1 || rate <= 0 || step <= 0 || postRate <= 0 || contention < 1) {
            throw new IllegalArgumentException("Performance parameters must be positive");
        }
        var protocol = http.baseUrl(System.getProperty("perf.baseUrl", "http://localhost:8082"))
                .acceptHeader("application/json")
                .disableCaching().disableWarmUp();
        var read = scenario("Paginated reads").exec(readPage());
        var posts = scenario("Balance update admission").exec(startJob());
        String mode = System.getProperty("perf.mode", "read");
        var setup = switch (mode) {
            case "smoke" -> setUp(scenario("Smoke").exec(readPage(), startJob()).injectOpen(atOnceUsers(1)));
            case "read" -> setUp(read.injectOpen(constantUsersPerSec(rate).during(seconds)));
            case "mixed" -> {
                int duration = levels * levelSeconds + (levels - 1) * rampSeconds;
                yield setUp(
                        read.injectOpen(incrementUsersPerSec(step).times(levels)
                                .eachLevelLasting(levelSeconds).separatedByRampsLasting(rampSeconds)
                                .startingFrom(rate)),
                        // These are admission attempts, never completion polls. Later attempts may start new jobs.
                        posts.injectOpen(atOnceUsers(contention), constantUsersPerSec(postRate).during(duration)));
            }
            default -> throw new IllegalArgumentException("perf.mode must be smoke, read, or mixed");
        };
        setup.protocols(protocol).assertions(
                global().failedRequests().percent().lte(Double.parseDouble(System.getProperty("perf.maxErrorPercent", "1"))),
                details("GET page").responseTime().percentile(95)
                        .lte(Integer.getInteger("perf.p95Millis", 1000)));
    }

    private ChainBuilder readPage() {
        int pages = (deposits - 1) / pageSize + 1;
        return exec(session -> session.set("page", ThreadLocalRandom.current().nextInt(pages)))
                .exec(http("GET page").get("/time-deposits").requestTimeout(10)
                        .queryParam("page", "#{page}").queryParam("size", pageSize)
                        .check(status().is(200), jsonPath("$.page").ofInt().is(session -> session.getInt("page")),
                                jsonPath("$.size").ofInt().is(pageSize),
                                jsonPath("$.totalElements").ofLong().is((long) deposits),
                                jsonPath("$.totalPages").ofInt().is(pages),
                                jsonPath("$.content[*].id").count().is(session ->
                                        Math.min(pageSize, deposits - session.getInt("page") * pageSize)),
                                jsonPath("$.content[0].withdrawals").exists()));
    }

    private ChainBuilder startJob() {
        return exec(http("POST admission").post("/time-deposits/balance-updates").requestTimeout(10)
                .check(status().in(202, 409), status().saveAs("postStatus"),
                        jsonPath("$.jobId").ofString().transform(java.util.UUID::fromString)
                                .transform(Object::toString).saveAs("jobId"))
                        .checkIf((response, session) -> response.status().code() == 202)
                                .then(jsonPath("$.status").is("STARTED"))
                        .checkIf((response, session) -> response.status().code() == 409)
                                .then(jsonPath("$.status").is("RUNNING")))
                .exec(session -> {
                    if (!session.isFailed()) {
                        String id = session.getString("jobId");
                        if (session.getInt("postStatus") == 202) {
                            accepted.increment();
                            acceptedIds.add(id);
                        } else {
                            busy.increment();
                            busyIds.add(id);
                        }
                    }
                    return session;
                });
    }

    @Override
    public void after() {
        Path output = Path.of(System.getProperty("perf.outcomes", "target/http-outcomes.json"));
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, """
                    {"accepted": %d, "busy": %d, "acceptedIds": [%s], "busyIds": [%s]}
                    """.formatted(accepted.sum(), busy.sum(), quoted(acceptedIds), quoted(busyIds)));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot write HTTP outcome counters", failure);
        }
    }

    private String quoted(Set<String> ids) {
        return ids.stream().sorted().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
    }
}
