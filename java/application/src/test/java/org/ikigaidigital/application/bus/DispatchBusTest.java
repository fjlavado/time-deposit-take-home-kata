package org.ikigaidigital.application.bus;

import org.ikigaidigital.application.command.Command;
import org.ikigaidigital.application.command.handler.CommandHandler;
import org.ikigaidigital.application.query.Query;
import org.ikigaidigital.application.query.handler.QueryHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DispatchBusTest {
    @Test
    void dispatchesOnlyTheMatchingCommandAndReturnsItsTypedResult() {
        var handled = new ArrayList<Integer>();
        var bus = new DefaultCommandBus(List.of(new DoubleCommandHandler(handled), new TextCommandHandler()));

        Integer doubled = bus.dispatch(new DoubleCommand(21));
        String text = bus.dispatch(new TextCommand("another handler"));

        assertThat(doubled).isEqualTo(42);
        assertThat(text).isEqualTo("another handler");
        assertThat(handled).containsExactly(21);
    }

    @Test
    void dispatchesOnlyTheMatchingQueryAndReturnsItsTypedResult() {
        var bus = new DefaultQueryBus(List.of(new LengthQueryHandler(), new TextQueryHandler()));

        Integer length = bus.dispatch(new LengthQuery("hello"));
        String text = bus.dispatch(new TextQuery("another handler"));

        assertThat(length).isEqualTo(5);
        assertThat(text).isEqualTo("another handler");
    }

    @Test
    void rejectsDuplicateCommandAndQueryRegistrations() {
        assertThatThrownBy(() -> new DefaultCommandBus(List.of(new TextCommandHandler(), new TextCommandHandler())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate command handler")
                .hasMessageContaining(TextCommand.class.getName());
        assertThatThrownBy(() -> new DefaultQueryBus(List.of(new TextQueryHandler(), new TextQueryHandler())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate query handler")
                .hasMessageContaining(TextQuery.class.getName());
    }

    @Test
    void identifiesRequestsWithoutRegisteredHandlers() {
        assertThatThrownBy(() -> new DefaultCommandBus(List.of()).dispatch(new TextCommand("missing")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No command handler")
                .hasMessageContaining(TextCommand.class.getName());
        assertThatThrownBy(() -> new DefaultQueryBus(List.of()).dispatch(new TextQuery("missing")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No query handler")
                .hasMessageContaining(TextQuery.class.getName());
    }

    @Test
    void registrationsCannotBeChangedThroughTheConstructorList() {
        var commands = new ArrayList<CommandHandler<?, ?>>(List.of(new TextCommandHandler()));
        var queries = new ArrayList<QueryHandler<?, ?>>(List.of(new TextQueryHandler()));
        var commandBus = new DefaultCommandBus(commands);
        var queryBus = new DefaultQueryBus(queries);
        commands.clear();
        queries.clear();

        assertThat(commandBus.dispatch(new TextCommand("command"))).isEqualTo("command");
        assertThat(queryBus.dispatch(new TextQuery("query"))).isEqualTo("query");
    }

    private record DoubleCommand(int value) implements Command<Integer> {}
    private record TextCommand(String value) implements Command<String> {}
    private record LengthQuery(String value) implements Query<Integer> {}
    private record TextQuery(String value) implements Query<String> {}

    private record DoubleCommandHandler(List<Integer> handled) implements CommandHandler<DoubleCommand, Integer> {
        @Override public Class<DoubleCommand> commandType() { return DoubleCommand.class; }
        @Override public Integer handle(DoubleCommand command) {
            handled.add(command.value());
            return command.value() * 2;
        }
    }

    private static final class TextCommandHandler implements CommandHandler<TextCommand, String> {
        @Override public Class<TextCommand> commandType() { return TextCommand.class; }
        @Override public String handle(TextCommand command) { return command.value(); }
    }

    private static final class LengthQueryHandler implements QueryHandler<LengthQuery, Integer> {
        @Override public Class<LengthQuery> queryType() { return LengthQuery.class; }
        @Override public Integer handle(LengthQuery query) { return query.value().length(); }
    }

    private static final class TextQueryHandler implements QueryHandler<TextQuery, String> {
        @Override public Class<TextQuery> queryType() { return TextQuery.class; }
        @Override public String handle(TextQuery query) { return query.value(); }
    }
}
