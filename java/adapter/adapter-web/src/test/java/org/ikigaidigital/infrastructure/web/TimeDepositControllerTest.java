package org.ikigaidigital.infrastructure.web;

import io.swagger.v3.parser.OpenAPIV3Parser;
import org.ikigaidigital.domain.model.PlanType;
import org.ikigaidigital.domain.model.TimeDeposit;
import org.ikigaidigital.domain.model.Withdrawal;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimeDepositControllerTest {
    @Test
    void retrievesExactResponseFieldsAndWithdrawalRepresentation() throws Exception {
        var deposit = new TimeDeposit(1, PlanType.BASIC, 1000, 31,
                List.of(new Withdrawal(2, new BigDecimal("12.34"), LocalDate.of(2026, 1, 2))));
        var mvc = MockMvcBuilders.standaloneSetup(new TimeDepositController(() -> List.of(deposit), () -> {})).build();

        mvc.perform(get("/time-deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].length()").value(5))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].planType").value("basic"))
                .andExpect(jsonPath("$[0].balance").value(1000))
                .andExpect(jsonPath("$[0].days").value(31))
                .andExpect(jsonPath("$[0].withdrawals[0].length()").value(3))
                .andExpect(jsonPath("$[0].withdrawals[0].id").value(2))
                .andExpect(jsonPath("$[0].withdrawals[0].amount").value(12.34))
                .andExpect(jsonPath("$[0].withdrawals[0].date").value("2026-01-02"));
    }

    @Test
    void delegatesEachUpdateAndReturnsNoBody() throws Exception {
        var invocations = new AtomicInteger();
        var mvc = MockMvcBuilders.standaloneSetup(new TimeDepositController(List::of, invocations::incrementAndGet)).build();

        mvc.perform(post("/time-deposits/balance-updates"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(post("/time-deposits/balance-updates"))
                .andExpect(status().isNoContent());

        assertThat(invocations.get()).isEqualTo(2);
        mvc.perform(get("/time-deposits")).andExpect(content().json("[]"));
    }

    @Test
    void openApiIsValidAndDescribesExactlyTheControllerOperationsAndFields() {
        var resource = Objects.requireNonNull(getClass().getResource("/openapi.yaml"));
        var parsed = new OpenAPIV3Parser().readLocation(resource.toExternalForm(), null, null);
        assertThat(parsed.getMessages()).isEmpty();
        var api = parsed.getOpenAPI();
        var documented = api.getPaths().entrySet().stream().flatMap(path ->
                path.getValue().readOperationsMap().keySet().stream().map(method -> method + " " + path.getKey()))
                .collect(Collectors.toSet());

        var mvc = MockMvcBuilders.standaloneSetup(new TimeDepositController(List::of, () -> {})).build();
        var context = Objects.requireNonNull(mvc.getDispatcherServlet().getWebApplicationContext());
        var mappings = context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods();
        var implemented = mappings.keySet().stream().flatMap(mapping -> mapping.getMethodsCondition().getMethods().stream()
                .flatMap(method -> mapping.getPatternValues().stream().map(path -> method + " " + path)))
                .collect(Collectors.toSet());

        assertThat(documented).containsExactlyInAnyOrder("GET /time-deposits", "POST /time-deposits/balance-updates");
        assertThat(implemented).isEqualTo(documented);
        var schema = api.getComponents().getSchemas().get("TimeDeposit");
        assertThat(schema.getProperties().keySet()).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(TimeDepositResponse.class.getRecordComponents()).map(component -> component.getName()).toList());
        assertThat(api.getPaths().get("/time-deposits").getGet().getResponses()).containsKey("200");
        assertThat(api.getPaths().get("/time-deposits/balance-updates").getPost().getResponses()).containsKey("204");
    }
}
