package jasmine.jragon.client;

import jasmine.jragon.client.transaction.Transaction;
import jasmine.jragon.command.ProtocolCommand;
import jasmine.jragon.tree.BTree;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static jasmine.jragon.response.ServerResponse.ACKNOWLEDGED;
import static jasmine.jragon.response.ServerResponse.DUPLICATE_KEYS;
import static jasmine.jragon.response.ServerResponse.EMPTY_TRANSACTION;
import static jasmine.jragon.response.ServerResponse.INSUFFICIENT_ARGUMENTS;
import static jasmine.jragon.response.ServerResponse.KEY_LOCKED_ISSUE;
import static jasmine.jragon.response.ServerResponse.NO_WRITES_ISSUE;
import static jasmine.jragon.response.ServerResponse.SHUTDOWN_IN_PROGRESS;
import static jasmine.jragon.response.ServerResponse.SUCCESS;
import static jasmine.jragon.response.ServerResponse.TRANSACTION_DNE;
import static jasmine.jragon.response.ServerResponse.TRANSACTION_EXPIRED_ISSUE;
import static jasmine.jragon.response.ServerResponse.TRANSACTION_IN_PROGRESS;
import static jasmine.jragon.response.ServerResponse.UNSUPPORTED_COMMAND;

@RequiredArgsConstructor(staticName = "from")
public final class TreeClient {
    private static final Logger LOG = LoggerFactory.getLogger(TreeClient.class);

    private final long userID;
    private final Set<String> globalKeyLock;
    private final BTree btree;
    private final SocketChannel servicedClient;

    private Transaction currentTransaction;

    public String acceptCommand(@NonNull String command, @NonNull AtomicBoolean serverClose) {
        var singleSplit = command.split(" +", 2);
        var protocol = ProtocolCommand.lookup(singleSplit[0]);

        var arguments = singleSplit.length == 1 ?
                new String[0] :
                singleSplit[1].split(" +");

        //Check command arguments
        //Handle locks
        //Conduct Response

        if (serverClose.get() && currentTransaction == null) {
            return SHUTDOWN_IN_PROGRESS.toString();
        } else if (serverClose.get() && currentTransaction.isExpired()) {
            eraseTransaction();
            return SHUTDOWN_IN_PROGRESS.toString();
        }

        return conductNormalResponse(protocol, arguments, serverClose);
    }

    private String conductNormalResponse(ProtocolCommand protocol,
                                         String[] arguments,
                                         AtomicBoolean serverClose) {
        var response = ACKNOWLEDGED;
        switch (protocol) {
            case READ, WRITE, CHECK -> {
                if (currentTransaction == null) {
                    return protocol.handleRequest(arguments, globalKeyLock, btree);
                } else if (currentTransaction.isNotExpired()) {
                    response = currentTransaction.addTransactionCommand(protocol, arguments, userID);
                } else {
                    eraseTransaction();
                    response = TRANSACTION_EXPIRED_ISSUE;
                }
            }
            case BEGIN_TRANSACTION -> {
                if (!protocol.containsSufficientArguments(arguments.length)) {
                    response = INSUFFICIENT_ARGUMENTS;
                } else if (currentTransaction != null) {
                    if (currentTransaction.isNotExpired()) {
                        response = TRANSACTION_IN_PROGRESS;
                    } else {
                        eraseTransaction();
                        response = TRANSACTION_EXPIRED_ISSUE;
                    }
                } else if (containsAnyLocked(arguments)) {
                    response = KEY_LOCKED_ISSUE;
                } else {
                    var uniqueArgs = Arrays.stream(arguments).distinct().toList();

                    setLocks(uniqueArgs);

                    LOG.info("Client {} started transaction {}", userID, uniqueArgs);

                    currentTransaction = new Transaction(btree, uniqueArgs);
                    if (uniqueArgs.size() == arguments.length) {
                        return currentTransaction.formatExpiration();
                    } else {
                        return String.format("%s%n%s", DUPLICATE_KEYS, currentTransaction.formatExpiration());
                    }
                }
            }
            case COMMIT_TRANSACTION, ABORT_TRANSACTION -> {
                if (currentTransaction == null) {
                    response = TRANSACTION_DNE;
                } else if (currentTransaction.isExpired()) {
                    eraseTransaction();
                    response = TRANSACTION_EXPIRED_ISSUE;
                } else if (protocol == ProtocolCommand.ABORT_TRANSACTION) {
                    eraseTransaction();
                    LOG.info("Client {} aborted", userID);
                } else {
                    if (currentTransaction.hasNoHistory()) {
                        response = EMPTY_TRANSACTION;
                    } else if (currentTransaction.containsOnlyReads()) {
                        response = NO_WRITES_ISSUE;
                    } else {
                        LOG.info("Client {} committed transaction. ({} keys released)",
                                userID, currentTransaction.getHeldKeys());
                        var transactionResponse = currentTransaction.transact();

                        eraseTransaction();

                        return SUCCESS + System.lineSeparator() + transactionResponse;
                    }
                }
            }
            case SHUTDOWN_SERVER -> serverClose.set(true);
            case UNSUPPORTED -> response = UNSUPPORTED_COMMAND;
        }

        return response.toString();
    }

    public boolean isDone() {
        return currentTransaction == null;
    }

    private boolean containsAnyLocked(String[] arguments) {
        return Arrays.stream(arguments)
                .anyMatch(globalKeyLock::contains);
    }

    private void setLocks(List<String> arguments) {
        globalKeyLock.addAll(arguments);
    }

    public void eraseTransaction() {
        if (currentTransaction != null) {
            releaseLocks(currentTransaction.getHeldKeys());

            currentTransaction = null;
        }
    }

    private void releaseLocks(List<String> heldKeys) {
        heldKeys.forEach(globalKeyLock::remove);
    }

    @Override
    public String toString() {
        try {
            if (servicedClient != null && servicedClient.isOpen()) {
                return String.format("Client %d (%s)", userID, servicedClient.getRemoteAddress());
            } else {
                return String.format("Client %d (CLOSED)", userID);
            }
        } catch (IOException e) {
            return String.format("Client %d (ERROR)", userID);
        }
    }
}
