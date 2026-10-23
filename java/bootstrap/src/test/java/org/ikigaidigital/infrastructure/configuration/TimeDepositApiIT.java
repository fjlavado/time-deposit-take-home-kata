package org.ikigaidigital.infrastructure.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TimeDepositApiIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void resetData() {
        jdbc.execute("TRUNCATE withdrawals, \"timeDeposits\" RESTART IDENTITY");
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days)
                VALUES (1, 'basic', 1000, 31), (2, 'premium', 1000, 45), (3, 'student', 1000, 366)
                """);
        jdbc.execute("""
                INSERT INTO withdrawals ("timeDepositId", amount, date)
                VALUES (1, 12.34, DATE '2026-01-02'), (1, 0.10, DATE '2026-01-03')
                """);
    }

    @Test
    void retrievesCompleteResponseThroughRealHttpAndDatabase() throws Exception {
        var response = request("GET", "/time-deposits");

        assertThat(response.statusCode()).isEqualTo(200);
        var page = json.readTree(response.body());
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("totalPages").asLong()).isEqualTo(1);
        var deposits = page.get("content");
        assertThat(deposits.size()).isEqualTo(3);
        var first = deposits.get(0);
        assertThat(first.size()).isEqualTo(5);
        assertThat(first.get("id").asInt()).isEqualTo(1);
        assertThat(first.get("planType").asString()).isEqualTo("basic");
        assertThat(first.get("balance").asDouble()).isEqualTo(1000);
        assertThat(first.get("days").asInt()).isEqualTo(31);
        assertThat(first.get("withdrawals").size()).isEqualTo(2);
        assertThat(first.get("withdrawals").get(0).get("amount").asDouble()).isEqualTo(12.34);
        assertThat(first.get("withdrawals").get(0).get("date").asString()).isEqualTo("2026-01-02");
        assertThat(deposits.get(1).get("withdrawals").isEmpty()).isTrue();
        assertThat(response.headers().firstValue("access-control-allow-origin")).contains("http://localhost:8081");
    }

    @Test
    void postPersistsAndSubsequentGetReturnsUpdatedBalances() throws Exception {
        var response = request("POST", "/time-deposits/balance-updates");

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        var deposits = json.readTree(request("GET", "/time-deposits").body()).get("content");
        assertThat(deposits.get(0).get("balance").asDouble()).isEqualTo(1000.83);
        assertThat(deposits.get(1).get("balance").asDouble()).isEqualTo(1000);
        assertThat(deposits.get(2).get("balance").asDouble()).isEqualTo(1000);
        assertThat(jdbc.queryForObject("SELECT balance FROM \"timeDeposits\" WHERE id = 1", BigDecimal.class))
                .isEqualByComparingTo("1000.83");
        assertThat(jdbc.queryForObject("SELECT days FROM \"timeDeposits\" WHERE id = 1", Integer.class)).isEqualTo(31);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawals", Integer.class)).isEqualTo(2);

        assertThat(request("POST", "/time-deposits/balance-updates").statusCode()).isEqualTo(204);
        assertThat(json.readTree(request("GET", "/time-deposits").body()).get("content").get(0).get("balance").asDouble())
                .isCloseTo(1001.66, within(1e-9));
    }

    @Test
    void bothEndpointsSupportAnEmptyDatabase() throws Exception {
        jdbc.execute("TRUNCATE withdrawals, \"timeDeposits\"");

        var empty = json.readTree(request("GET", "/time-deposits").body());
        assertThat(empty.get("content").isEmpty()).isTrue();
        assertThat(empty.get("totalElements").asLong()).isZero();
        assertThat(empty.get("totalPages").asLong()).isZero();
        assertThat(request("POST", "/time-deposits/balance-updates").statusCode()).isEqualTo(204);
        assertThat(json.readTree(request("GET", "/time-deposits").body()).get("content").isEmpty()).isTrue();
    }

    @Test
    void persistsHalfCentInterestUsingDecimalHalfUpRounding() throws Exception {
        jdbc.execute("TRUNCATE withdrawals, \"timeDeposits\" RESTART IDENTITY");
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days)
                VALUES (1, 'basic', 18, 31), (2, 'student', 6, 31)
                """);

        assertThat(request("POST", "/time-deposits/balance-updates").statusCode()).isEqualTo(204);

        var deposits = json.readTree(request("GET", "/time-deposits").body()).get("content");
        assertThat(deposits.get(0).get("balance").asDouble()).isEqualTo(18.02);
        assertThat(deposits.get(1).get("balance").asDouble()).isEqualTo(6.02);
        assertThat(jdbc.queryForObject("SELECT balance FROM \"timeDeposits\" WHERE id = 1", BigDecimal.class))
                .isEqualByComparingTo("18.02");
        assertThat(jdbc.queryForObject("SELECT balance FROM \"timeDeposits\" WHERE id = 2", BigDecimal.class))
                .isEqualByComparingTo("6.02");
    }

    @Test
    void paginatesResultsAndPreservesTotalsBeyondTheLastPage() throws Exception {
        var firstResponse = request("GET", "/time-deposits?page=0&size=2");
        assertThat(firstResponse.statusCode()).isEqualTo(200);
        var first = json.readTree(firstResponse.body());
        assertThat(first.get("content").size()).isEqualTo(2);
        assertThat(first.get("content").get(0).get("id").asInt()).isEqualTo(1);
        assertThat(first.get("content").get(1).get("id").asInt()).isEqualTo(2);
        assertThat(first.get("content").get(0).get("withdrawals").size()).isEqualTo(2);
        assertThat(first.get("totalElements").asLong()).isEqualTo(3);
        assertThat(first.get("totalPages").asLong()).isEqualTo(2);

        var last = json.readTree(request("GET", "/time-deposits?page=1&size=2").body());
        assertThat(last.get("content").size()).isEqualTo(1);
        assertThat(last.get("content").get(0).get("id").asInt()).isEqualTo(3);
        assertThat(last.get("page").asInt()).isEqualTo(1);
        assertThat(last.get("size").asInt()).isEqualTo(2);

        var beyond = json.readTree(request("GET", "/time-deposits?page=2&size=2").body());
        assertThat(beyond.get("content").isEmpty()).isTrue();
        assertThat(beyond.get("totalElements").asLong()).isEqualTo(3);
        assertThat(beyond.get("totalPages").asLong()).isEqualTo(2);
    }

    private HttpResponse<String> request(String method, String path) throws Exception {
        var uri = URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Origin", "http://localhost:8081")
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
