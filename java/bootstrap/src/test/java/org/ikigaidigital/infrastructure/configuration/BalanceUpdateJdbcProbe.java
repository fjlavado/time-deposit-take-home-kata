package org.ikigaidigital.infrastructure.configuration;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Observe actual driver calls, rather than mocking streaming/batching behavior. */
final class BalanceUpdateJdbcProbe {
    final AtomicInteger selects = new AtomicInteger();
    final AtomicInteger openStatements = new AtomicInteger();
    final AtomicInteger openResults = new AtomicInteger();
    final AtomicInteger closedResults = new AtomicInteger();
    final List<Integer> fetchSizes = new CopyOnWriteArrayList<>();
    final List<Boolean> autoCommit = new CopyOnWriteArrayList<>();
    final List<Integer> batchSizes = new CopyOnWriteArrayList<>();

    void reset() {
        selects.set(0);
        openStatements.set(0);
        openResults.set(0);
        closedResults.set(0);
        fetchSizes.clear();
        autoCommit.clear();
        batchSizes.clear();
    }

    DataSource wrap(DataSource source) {
        return proxy(DataSource.class, source, invocation -> {
            var result = invocation.proceed();
            return invocation.getMethod().getName().equals("getConnection")
                    ? connection((Connection) result) : result;
        });
    }

    private Connection connection(Connection connection) {
        var wrapped = new Connection[1];
        wrapped[0] = proxy(Connection.class, connection, invocation -> {
            var result = invocation.proceed();
            if (invocation.getMethod().getName().equals("prepareStatement")) {
                var sql = ((String) invocation.getArguments()[0]).stripLeading();
                boolean cursor = sql.contains("WHERE days > last_processed_days");
                boolean batch = sql.startsWith("UPDATE \"timeDeposits\"") && sql.contains("version = version + 1");
                if (cursor || batch) {
                    return statement((PreparedStatement) result, wrapped[0], cursor);
                }
            }
            return result;
        });
        return wrapped[0];
    }

    private PreparedStatement statement(PreparedStatement statement, Connection connection, boolean cursor) {
        var closed = new AtomicBoolean();
        var batchCount = new AtomicInteger();
        if (cursor) {
            openStatements.incrementAndGet();
        }
        return proxy(PreparedStatement.class, statement, invocation -> {
            String method = invocation.getMethod().getName();
            // JdbcTemplate's stream close uses Statement.getConnection(). Keep
            // that identity equal to the transaction-bound observed connection.
            if (method.equals("getConnection")) {
                return connection;
            }
            if (method.equals("addBatch")) {
                batchCount.incrementAndGet();
            } else if (method.equals("executeBatch")) {
                batchSizes.add(batchCount.getAndSet(0));
            } else if (method.equals("executeQuery") && cursor) {
                selects.incrementAndGet();
                fetchSizes.add(statement.getFetchSize());
                autoCommit.add(connection.getAutoCommit());
            }
            var result = invocation.proceed();
            if (method.equals("executeQuery") && cursor) {
                return resultSet((ResultSet) result);
            }
            if (method.equals("close") && cursor && closed.compareAndSet(false, true)) {
                openStatements.decrementAndGet();
            }
            return result;
        });
    }

    private ResultSet resultSet(ResultSet rows) {
        var closed = new AtomicBoolean();
        openResults.incrementAndGet();
        return proxy(ResultSet.class, rows, invocation -> {
            var result = invocation.proceed();
            if (invocation.getMethod().getName().equals("close") && closed.compareAndSet(false, true)) {
                openResults.decrementAndGet();
                closedResults.incrementAndGet();
            }
            return result;
        });
    }

    private static <T> T proxy(Class<T> type, T target, MethodInterceptor interceptor) {
        var proxy = new ProxyFactory();
        proxy.setInterfaces(type);
        proxy.setTarget(target);
        proxy.addAdvice(interceptor);
        return type.cast(proxy.getProxy());
    }
}
