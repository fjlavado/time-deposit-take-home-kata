package org.ikigaidigital.infrastructure.configuration;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Observe the ID-only cursor and aggregate reads without coupling tests to generated write SQL. */
final class BalanceUpdateJdbcProbe {
    final AtomicInteger idReads = new AtomicInteger();
    final AtomicInteger aggregateReads = new AtomicInteger();
    final AtomicInteger cursorRows = new AtomicInteger();
    final AtomicInteger openStatements = new AtomicInteger();
    final AtomicInteger openResults = new AtomicInteger();
    final AtomicInteger closedResults = new AtomicInteger();
    final List<Integer> fetchSizes = new CopyOnWriteArrayList<>();
    final List<Boolean> autoCommit = new CopyOnWriteArrayList<>();

    void reset() {
        idReads.set(0);
        aggregateReads.set(0);
        cursorRows.set(0);
        openStatements.set(0);
        openResults.set(0);
        closedResults.set(0);
        fetchSizes.clear();
        autoCommit.clear();
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
                if (sql.startsWith("SELECT id FROM \"timeDeposits\"")) {
                    return cursorStatement((PreparedStatement) result, wrapped[0]);
                }
                var upper = sql.toUpperCase(Locale.ROOT);
                if ((upper.startsWith("SELECT") || upper.startsWith("WITH")) && sql.contains("\"timeDeposits\"")) {
                    aggregateReads.incrementAndGet();
                }
            }
            return result;
        });
        return wrapped[0];
    }

    private PreparedStatement cursorStatement(PreparedStatement statement, Connection connection) {
        var closed = new AtomicBoolean();
        openStatements.incrementAndGet();
        return proxy(PreparedStatement.class, statement, invocation -> {
            String method = invocation.getMethod().getName();
            if (method.equals("getConnection")) {
                return connection;
            }
            if (method.equals("executeQuery")) {
                idReads.incrementAndGet();
                fetchSizes.add(statement.getFetchSize());
                autoCommit.add(connection.getAutoCommit());
            }
            var result = invocation.proceed();
            if (method.equals("executeQuery")) {
                return resultSet((ResultSet) result);
            }
            if (method.equals("close") && closed.compareAndSet(false, true)) {
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
            if (invocation.getMethod().getName().equals("next") && Boolean.TRUE.equals(result)) {
                cursorRows.incrementAndGet();
            }
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
