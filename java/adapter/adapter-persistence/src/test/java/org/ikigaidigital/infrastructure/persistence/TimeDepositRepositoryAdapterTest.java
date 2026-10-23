package org.ikigaidigital.infrastructure.persistence;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.infrastructure.persistence.mapper.TimeDepositPersistenceMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeDepositRepositoryAdapterTest {
    @ParameterizedTest
    @MethodSource("unverifiableCounts")
    void rejectsStaleFailedUnknownOrMissingRowCounts(int[][] counts) {
        var jdbc = new JdbcTemplate() {
            @Override
            public <T> int[][] batchUpdate(String sql, Collection<T> values, int batchSize,
                                           ParameterizedPreparedStatementSetter<T> setter) {
                return counts;
            }
        };
        var adapter = new TimeDepositRepositoryAdapter(null, new TimeDepositPersistenceMapper(), jdbc);
        var batch = List.of(new TimeDeposit(1, PlanType.BASIC, 1000.0, 31),
                new TimeDeposit(2, PlanType.BASIC, 1000.0, 31));

        assertThatThrownBy(() -> adapter.saveBatch(batch)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    static Stream<Arguments> unverifiableCounts() {
        return Stream.of(new int[][]{{1, 0}}, new int[][]{{1, Statement.EXECUTE_FAILED}},
                new int[][]{{1, Statement.SUCCESS_NO_INFO}}, new int[0][], new int[][]{{1}})
                .map(counts -> Arguments.of((Object) counts));
    }
}
