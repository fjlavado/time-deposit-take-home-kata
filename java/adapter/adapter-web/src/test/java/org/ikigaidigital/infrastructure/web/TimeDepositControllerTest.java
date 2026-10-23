package org.ikigaidigital.infrastructure.web;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.oas.models.media.Schema;
import org.ikigaidigital.application.command.StartBalanceUpdateJobCommand;
import org.ikigaidigital.application.job.BalanceUpdateJobResult;
import org.ikigaidigital.application.port.in.CommandBus;
import org.ikigaidigital.application.port.in.QueryBus;
import org.ikigaidigital.application.query.RetrieveTimeDepositsQuery;
import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.domain.model.Withdrawal;
import org.ikigaidigital.domain.model.Page;
import org.ikigaidigital.infrastructure.web.generated.model.TimeDepositPageResponse;
import org.ikigaidigital.infrastructure.web.generated.model.TimeDepositResponse;
import org.ikigaidigital.infrastructure.web.generated.model.WithdrawalResponse;
import org.ikigaidigital.infrastructure.web.generated.model.BalanceUpdateJobResponse;
import org.ikigaidigital.infrastructure.web.mapper.TimeDepositApiMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimeDepositControllerTest {
    private final CommandBus commands = mock(CommandBus.class);
    private final QueryBus queries = mock(QueryBus.class);

    @BeforeEach
    void emptyQueryResult() {
        when(queries.dispatch(new RetrieveTimeDepositsQuery(0, 20))).thenReturn(new Page<>(List.of(), 0, 20, 0));
    }

    @Test
    void retrievesExactResponseFieldsAndWithdrawalRepresentation() throws Exception {
        var deposit = new TimeDeposit(1, PlanType.BASIC, 1000.0, 31,
                List.of(new Withdrawal(2, new BigDecimal("12.34"), LocalDate.of(2026, 1, 2))));
        when(queries.dispatch(new RetrieveTimeDepositsQuery(0, 20)))
                .thenReturn(new Page<>(List.of(deposit), 0, 20, 1));
        var mvc = MockMvcBuilders.standaloneSetup(controller()).build();

        mvc.perform(get("/time-deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].length()").value(5))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].planType").value("basic"))
                .andExpect(jsonPath("$.content[0].balance").value(1000))
                .andExpect(jsonPath("$.content[0].days").value(31))
                .andExpect(jsonPath("$.content[0].withdrawals[0].length()").value(3))
                .andExpect(jsonPath("$.content[0].withdrawals[0].id").value(2))
                .andExpect(jsonPath("$.content[0].withdrawals[0].amount").value(12.34))
                .andExpect(jsonPath("$.content[0].withdrawals[0].date").value("2026-01-02"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(queries).dispatch(new RetrieveTimeDepositsQuery(0, 20));
        verifyNoInteractions(commands);
    }

    @Test
    void returnsAcceptedOrConflictWithTheSameExecutionUuid() throws Exception {
        var id = UUID.randomUUID();
        when(commands.dispatch(new StartBalanceUpdateJobCommand()))
                .thenReturn(new BalanceUpdateJobResult(id, BalanceUpdateJobResult.Status.STARTED))
                .thenReturn(new BalanceUpdateJobResult(id, BalanceUpdateJobResult.Status.RUNNING));
        var mvc = MockMvcBuilders.standaloneSetup(controller()).build();

        mvc.perform(post("/time-deposits/balance-updates"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.jobId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("STARTED"))
                .andExpect(jsonPath("$.message").value("Balance update job accepted."));
        mvc.perform(post("/time-deposits/balance-updates"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.jobId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.message").value("A balance update job is already running."));

        verify(commands, times(2)).dispatch(new StartBalanceUpdateJobCommand());
        mvc.perform(get("/time-deposits")).andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"student", "premium", "FuturePlan", "", " basic"})
    void exposesPlanTypeAsItsExactStringValue(String plan) throws Exception {
        var deposit = new TimeDeposit(1, new PlanType(plan), 1000.0, 46);
        when(queries.dispatch(new RetrieveTimeDepositsQuery(0, 20)))
                .thenReturn(new Page<>(List.of(deposit), 0, 20, 1));
        var mvc = MockMvcBuilders.standaloneSetup(controller()).build();

        mvc.perform(get("/time-deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].planType").value(plan));
    }

    @Test
    void forwardsPaginationParametersAndReturnsMetadata() throws Exception {
        when(queries.dispatch(new RetrieveTimeDepositsQuery(2, 5)))
                .thenReturn(new Page<>(List.of(), 2, 5, 7));
        var mvc = MockMvcBuilders.standaloneSetup(controller()).build();

        mvc.perform(get("/time-deposits").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(queries).dispatch(new RetrieveTimeDepositsQuery(2, 5));
        verifyNoInteractions(commands);
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

        var mvc = MockMvcBuilders.standaloneSetup(controller()).build();
        var context = Objects.requireNonNull(mvc.getDispatcherServlet().getWebApplicationContext());
        var mappings = context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods();
        var implemented = mappings.keySet().stream().flatMap(mapping -> mapping.getMethodsCondition().getMethods().stream()
                .flatMap(method -> mapping.getPatternValues().stream().map(path -> method + " " + path)))
                .collect(Collectors.toSet());

        assertThat(documented).containsExactlyInAnyOrder("GET /time-deposits", "POST /time-deposits/balance-updates");
        assertThat(implemented).isEqualTo(documented);
        var responseTypes = List.of(TimeDepositPageResponse.class, TimeDepositResponse.class,
                WithdrawalResponse.class, BalanceUpdateJobResponse.class);
        for (var responseType : responseTypes) {
            var schema = api.getComponents().getSchemas().get(responseType.getSimpleName().replace("Response", ""));
            assertThat(schema.getProperties().keySet()).containsExactlyInAnyOrderElementsOf(
                    Arrays.stream(responseType.getDeclaredFields())
                            .filter(field -> !Modifier.isStatic(field.getModifiers()))
                            .map(field -> field.getName()).toList());
        }
        assertThat(api.getPaths().get("/time-deposits").getGet().getResponses()).containsKey("200");
        assertThat(api.getPaths().get("/time-deposits").getGet().getParameters())
                .extracting(parameter -> parameter.getName()).containsExactly("page", "size");
        var updates = api.getPaths().get("/time-deposits/balance-updates").getPost().getResponses();
        assertThat(updates).containsOnlyKeys("202", "409");
        var jobId = (Schema<?>) api.getComponents().getSchemas().get("BalanceUpdateJob").getProperties().get("jobId");
        assertThat(jobId.getFormat()).isEqualTo("uuid");
    }

    private TimeDepositController controller() {
        return new TimeDepositController(commands, queries, new TimeDepositApiMapper());
    }
}
