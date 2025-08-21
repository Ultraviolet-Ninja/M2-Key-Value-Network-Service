package jasmine.jragon.response;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ServerResponse {
    NONE(""),
    SUCCESS("Successful command: "),
    ACKNOWLEDGED("Command acknowledged"),
    INSUFFICIENT_ARGUMENTS("Incorrect Number of Arguments"),
    DUPLICATE_KEYS("Duplicate Keys detected in TRANSACT"),
    UNSUPPORTED_COMMAND("Unsupported command"),
    INVALID_TRANSACTION_COMMAND("Invalid Transaction Command"),
    VALUE_OR_NULL("null") {
        @Override
        public String useOrDefault(String newValue) {
            return newValue == null || newValue.isEmpty() ? VALUE_OR_NULL.defaultResponse : newValue;
        }
    },
    KEY_LOCKED_ISSUE("Requested key(s) is locked"),
    TRANSACTION_DNE("No such transaction exists"),
    EMPTY_TRANSACTION("Transaction has no history. Nothing committed"),
    TRANSACTION_IN_PROGRESS("Transaction In Progress"),
    TRANSACTION_EXPIRED_ISSUE("Transaction expired"),
    TRANSACTION_DOES_NOT_HAVE_KEY("Transaction does not have key"),
    NO_WRITES_ISSUE("No writes submitted"),
    SHUTDOWN_IN_PROGRESS("Shutdown in progress. Cannot accept new commands"),
    UNKNOWN_CLIENT("Unknown client");

    private final String defaultResponse;

    @Override
    public String toString() {
        return defaultResponse;
    }

    public String useOrDefault(String ignored) {
        return defaultResponse;
    }
}
