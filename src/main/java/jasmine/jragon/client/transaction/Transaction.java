package jasmine.jragon.client.transaction;

import jasmine.jragon.command.ProtocolCommand;
import jasmine.jragon.response.ServerResponse;
import jasmine.jragon.tree.BTree;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("ClassCanBeRecord")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Transaction {
    private static final Logger LOG = LoggerFactory.getLogger(Transaction.class.getName());
    private static final DateTimeFormatter STANDARD_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int EXPIRATION_MINUTES = 15;

    private final BTree btree;
    private final LocalDateTime expiration;
    @Getter
    private final List<String> heldKeys;
    private final List<IntermediateCommand> transactionCommands;

    public Transaction(BTree btree, List<String> heldKeys) {
        this(
                btree,
                LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES),
                Collections.unmodifiableList(heldKeys),
                new ArrayList<>()
        );
    }

    public boolean containsOnlyReads() {
        return transactionCommands.stream()
                .map(IntermediateCommand::protocol)
                .noneMatch(ProtocolCommand.WRITE::equals);
    }

    public boolean hasNoHistory() {
        return transactionCommands.isEmpty();
    }

    public ServerResponse addTransactionCommand(ProtocolCommand command,
                                                String[] arguments,
                                                long clientID) {
        if (!command.containsSufficientArguments(arguments.length)) {
            return ServerResponse.INSUFFICIENT_ARGUMENTS;
        }

        if (command.isTransactionCommand() && isKeyHeld(arguments[0])) {
            if (command.isWriteAhead()) {
                LOG.info("Client {} wrote {} -> {}", clientID, arguments[0], arguments[1]);
            }

            transactionCommands.add(new IntermediateCommand(
                    command, arguments, LocalDateTime.now()
            ));
            return ServerResponse.ACKNOWLEDGED;
        }

        return command.isTransactionCommand() ?
                ServerResponse.TRANSACTION_DOES_NOT_HAVE_KEY :
                ServerResponse.INVALID_TRANSACTION_COMMAND;
    }

    public String transact() {
        var stringBuilder = new StringBuilder();

        for (var command : transactionCommands) {
            //Empty set used because exclusivity is guaranteed here
            var response = command.protocol.handleRequest(command.arguments, Collections.emptySet(), btree);

            stringBuilder.append(response)
                    .append("\n");
        }

        return stringBuilder.toString();
    }

    public boolean isKeyHeld(String argument) {
        return heldKeys.contains(argument);
    }

    public boolean isNotExpired() {
        return LocalDateTime.now().isBefore(expiration);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiration);
    }

    public String formatExpiration() {
        return "Transaction started & expires at " + STANDARD_FORMAT.format(expiration);
    }

    @Override
    public String toString() {
        return String.format("Transaction Keys: %s - Command Count: %d (Expires at %s)",
                heldKeys, transactionCommands.size(), STANDARD_FORMAT.format(expiration));
    }

    private record IntermediateCommand(ProtocolCommand protocol, String[] arguments, LocalDateTime timestamp) {}
}
