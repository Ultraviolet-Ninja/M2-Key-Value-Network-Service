package jasmine.jragon.command;

import jasmine.jragon.response.ServerResponse;
import jasmine.jragon.tree.BTree;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

import static jasmine.jragon.command.ProtocolCommand.ArgumentType.*;

@Getter
@RequiredArgsConstructor
public enum ProtocolCommand implements RequestHandler {
    READ("GET", SINGLE_KEY_ONLY) {
        @Override
        public String handleRequest(@NonNull String @NonNull [] arguments, @NonNull Set<String> lockSet, @NonNull BTree tree) {
            if (containsSufficientArguments(arguments.length) &&
                    !containsLockedKey(arguments, lockSet)) {
                return ServerResponse.VALUE_OR_NULL.useOrDefault(
                        tree.get(arguments[0]).orElse("")
                );
            }

            var response = ServerResponse.NONE;
            if (!containsSufficientArguments(arguments.length)) {
                response = ServerResponse.INSUFFICIENT_ARGUMENTS;
            } else if (containsLockedKey(arguments, lockSet)) {
                response = ServerResponse.KEY_LOCKED_ISSUE;
            }

            return response.toString();
        }
    },
    WRITE("PUT", KEY_VALUE_PAIR) {
        @Override
        public String handleRequest(@NonNull String @NonNull [] arguments, @NonNull Set<String> lockSet, @NonNull BTree tree) {
            if (containsSufficientArguments(arguments.length) && !containsLockedKey(arguments, lockSet)) {
                return ServerResponse.VALUE_OR_NULL.useOrDefault(
                        String.valueOf(tree.put(arguments[0], arguments[1]))
                );
            }

            var response = ServerResponse.NONE;
            if (!containsSufficientArguments(arguments.length)) {
                response = ServerResponse.INSUFFICIENT_ARGUMENTS;
            } else if (containsLockedKey(arguments, lockSet)) {
                response = ServerResponse.KEY_LOCKED_ISSUE;
            }

            return response.toString();
        }
    },
    CHECK("CONTAINS", SINGLE_KEY_ONLY) {
        @Override
        public String handleRequest(@NonNull String @NonNull [] arguments, @NonNull Set<String> lockSet, @NonNull BTree tree) {
            if (containsSufficientArguments(arguments.length) &&
                    !containsLockedKey(arguments, lockSet)) {
                return ServerResponse.VALUE_OR_NULL.useOrDefault(
                        String.valueOf(tree.contains(arguments[0]))
                );
            }

            var response = ServerResponse.NONE;
            if (!containsSufficientArguments(arguments.length)) {
                response = ServerResponse.INSUFFICIENT_ARGUMENTS;
            } else if (containsLockedKey(arguments, lockSet)) {
                response = ServerResponse.KEY_LOCKED_ISSUE;
            }

            return response.toString();
        }
    },
    BEGIN_TRANSACTION("TRANSACT", AT_LEAST_ONE_LEY),
    COMMIT_TRANSACTION("COMMIT", NONE),
    ABORT_TRANSACTION("ABORT", NONE),
    SHUTDOWN_SERVER("SHUTDOWN", NONE),
    UNSUPPORTED("UNSUPPORTED", NONE) {
        @Override
        public String handleRequest(@NonNull String @NonNull [] arguments, @NonNull Set<String> lockSet, @NonNull BTree tree) {
            return ServerResponse.UNSUPPORTED_COMMAND.toString();
        }
    };

    private final String commandName;
    private final ArgumentType type;

    public boolean containsSufficientArguments(int argumentCount) {
        return type.test(argumentCount);
    }

    public boolean isTransactionCommand() {
        return switch (this) {
            case READ, WRITE, CHECK -> true;
            default -> false;
        };
    }

    public boolean isWriteAhead() {
        return switch (this) {
            case WRITE, BEGIN_TRANSACTION, COMMIT_TRANSACTION, ABORT_TRANSACTION -> true;
            default -> false;
        };
    }

    private static final Map<String, ProtocolCommand> LOOKUP_MAP;

    public static ProtocolCommand lookup(@NonNull String commandName) {
        return LOOKUP_MAP.getOrDefault(commandName.toUpperCase(), UNSUPPORTED);
    }

    private static boolean containsLockedKey(String[] keys, Set<String> lockSet) {
        return Arrays.stream(keys)
                .anyMatch(lockSet::contains);
    }

    static {
        LOOKUP_MAP = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(
                        cmd -> cmd.commandName,
                        Function.identity(),
                        (l, r) -> {throw new IllegalStateException("Cannot have 2 enums with the same command name. (" + l + ", " + r + ")");}
                ));
    }

    public enum ArgumentType implements IntPredicate {
        NONE {
            @Override
            public boolean test(int value) {
                return value == 0;
            }
        },
        SINGLE_KEY_ONLY {
            @Override
            public boolean test(int value) {
                return value == 1;
            }
        },
        KEY_VALUE_PAIR {
            @Override
            public boolean test(int value) {
                return value == 2;
            }
        },
        AT_LEAST_ONE_LEY {
            @Override
            public boolean test(int value) {
                return value >= 1;
            }
        }
    }
}