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
        var deposits = json.readTree(response.body());
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
        var deposits = json.readTree(request("GET", "/time-deposits").body());
        assertThat(deposits.get(0).get("balance").asDouble()).isEqualTo(1000.83);
        assertThat(deposits.get(1).get("balance").asDouble()).isEqualTo(1000);
        assertThat(deposits.get(2).get("balance").asDouble()).isEqualTo(1000);
        assertThat(jdbc.queryForObject("SELECT balance FROM \"timeDeposits\" WHERE id = 1", BigDecimal.class))
                .isEqualByComparingTo("1000.83");
        assertThat(jdbc.queryForObject("SELECT days FROM \"timeDeposits\" WHERE id = 1", Integer.class)).isEqualTo(31);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawals", Integer.class)).isEqualTo(2);

        assertThat(request("POST", "/time-deposits/balance-updates").statusCode()).isEqualTo(204);
        assertThat(json.readTree(request("GET", "/time-deposits").body()).get(0).get("balance").asDouble())
                // The final binary-double balance is intentionally not rounded to cents.
                .isEqualTo(1001.6600000000001);
    }

    @Test
    void bothEndpointsSupportAnEmptyDatabase() throws Exception {
        jdbc.execute("TRUNCATE withdrawals, \"timeDeposits\"");

        assertThat(request("GET", "/time-deposits").body()).isEqualTo("[]");
        assertThat(request("POST", "/time-deposits/balance-updates").statusCode()).isEqualTo(204);
        assertThat(request("GET", "/time-deposits").body()).isEqualTo("[]");
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
