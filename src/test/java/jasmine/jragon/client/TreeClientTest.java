package jasmine.jragon.client;

import jasmine.jragon.tree.BTree;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("DataFlowIssue")
public class TreeClientTest {
    private static final Random RANDOM = new Random();
    private static final String EXPIRATION_DATE_REGEXP = "Transaction started & expires at \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}";
    public static final String EXPIRATION_WITH_WARNING_REGEXP = "Duplicate Keys detected in TRANSACT\\s+" + EXPIRATION_DATE_REGEXP;

    private Set<String> globalKeyLock;
    private BTree btree;
    private TreeClient clientUnderTest;
    private final AtomicBoolean shutdownAtomic = new AtomicBoolean(false);

    @BeforeMethod
    public void beforeMethod() {
        shutdownAtomic.set(false);

        btree = new BTree(5);

        btree.put("1", "a");
        btree.put("2", "b");
        btree.put("3", "c");
        btree.put("4", "d");
        btree.put("5", "e");
        btree.put("6", "f");
        btree.put("7", "g");
        btree.put("8", "h");
        btree.put("9", "i");
        btree.put("10", "j");

        globalKeyLock = new HashSet<>();

        clientUnderTest = TreeClient.from(RANDOM.nextLong(), globalKeyLock, btree, null);
    }

    @Test
    public void initializedTest() {
        assertTrue(clientUnderTest.isDone());

        var desc = clientUnderTest.toString();
        assertTrue(desc.startsWith("Client "));
        assertTrue(desc.contains("CLOSED"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void acceptNullCommand() {
        clientUnderTest.acceptCommand(null, null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void acceptNullAtomic() {
        clientUnderTest.acceptCommand("", null);
    }

    @Test
    public void acceptBlankTest() {
        var unsupportedResponse = clientUnderTest.acceptCommand("", shutdownAtomic);

        assertEquals(
                unsupportedResponse,
                "Unsupported command"
        );
    }

    @Test
    public void noKeyTransactionTest() {
        var response = clientUnderTest.acceptCommand("TRANSACT", shutdownAtomic);

        assertEquals(
                response,
                "Incorrect Number of Arguments"
        );
    }

    @Test
    public void duplicateKeyTransactionTest() {
        var firstResponse = clientUnderTest.acceptCommand("TRANSACT 1 1 2 3", shutdownAtomic);

        assertFalse(shutdownAtomic.get());
        assertTrue(firstResponse.matches(EXPIRATION_WITH_WARNING_REGEXP));
    }

    @Test
    public void doubleTransactionTest() {
        var firstResponse = clientUnderTest.acceptCommand("TRANSACT 1 2 3", shutdownAtomic);

        assertFalse(clientUnderTest.isDone());
        assertFalse(shutdownAtomic.get());
        assertFalse(globalKeyLock.isEmpty());
        assertTrue(firstResponse.matches(EXPIRATION_DATE_REGEXP));

        var secondResponse = clientUnderTest.acceptCommand("TRANSACT 1 2 3", shutdownAtomic);

        assertFalse(shutdownAtomic.get());
        assertEquals(secondResponse, "Transaction In Progress");
    }

    @Test
    public void noTransactionTest() {
        var commitResponse = clientUnderTest.acceptCommand("COMMIT", shutdownAtomic);

        assertFalse(shutdownAtomic.get());
        assertEquals(commitResponse, "No such transaction exists");

        assertTrue(clientUnderTest.isDone());

        var abortResponse = clientUnderTest.acceptCommand("ABORT", shutdownAtomic);

        assertFalse(shutdownAtomic.get());
        assertEquals(abortResponse, "No such transaction exists");

        assertTrue(clientUnderTest.isDone());
    }

    @Test
    public void shutdownFromClientTest() {
        var acknowledgement = clientUnderTest.acceptCommand("SHUTDOWN", shutdownAtomic);

        assertTrue(shutdownAtomic.get());
        assertEquals(acknowledgement, "Command acknowledged");
    }

    @Test(dependsOnMethods = "shutdownFromClientTest")
    public void enterInShutdownTest() {
        shutdownAtomic.set(true);

        var shutdownResponse = clientUnderTest.acceptCommand("GET 1", shutdownAtomic);

        assertTrue(shutdownAtomic.get());
        assertEquals(
                shutdownResponse,
                "Shutdown in progress. Cannot accept new commands"
        );
    }

    @Test(dependsOnMethods = "doubleTransactionTest")
    public void commitNothingTest() {
        clientUnderTest.acceptCommand("TRANSACT 1 2 3", shutdownAtomic);

        var commitResponse = clientUnderTest.acceptCommand("COMMIT", shutdownAtomic);

        assertEquals(
                commitResponse,
                "Transaction has no history. Nothing committed"
        );
    }

    @Test(dependsOnMethods = "doubleTransactionTest")
    public void commitReadOnlyTest() {
        clientUnderTest.acceptCommand("TRANSACT 1 2 3", shutdownAtomic);

        clientUnderTest.acceptCommand("GET 1", shutdownAtomic);
        clientUnderTest.acceptCommand("GET 2", shutdownAtomic);
        clientUnderTest.acceptCommand("GET 3", shutdownAtomic);

        var commitResponse = clientUnderTest.acceptCommand("COMMIT", shutdownAtomic);
        assertEquals(
                commitResponse,
                "No writes submitted"
        );
    }

    @Test(dependsOnMethods = "doubleTransactionTest")
    public void commitMalformedCommandsTest() {
        clientUnderTest.acceptCommand("TRANSACT 1 2 3", shutdownAtomic);

        assertEquals(
                clientUnderTest.acceptCommand("GET", shutdownAtomic),
                "Incorrect Number of Arguments"
        );

        assertEquals(
                clientUnderTest.acceptCommand("PUT 1", shutdownAtomic),
                "Incorrect Number of Arguments"
        );

        assertEquals(
                clientUnderTest.acceptCommand("CONTAINS 1 2 4", shutdownAtomic),
                "Incorrect Number of Arguments"
        );

        var commitResponse = clientUnderTest.acceptCommand("COMMIT", shutdownAtomic);
        assertEquals(
                commitResponse,
                "Transaction has no history. Nothing committed"
        );
    }

    @Test(dependsOnMethods = "doubleTransactionTest")
    public void keyOutOfTransactionTest() {
        clientUnderTest.acceptCommand("TRANSACT 1 2 3", shutdownAtomic);

        var keyResponse = clientUnderTest.acceptCommand("GET 4", shutdownAtomic);

        assertEquals(
                keyResponse,
                "Transaction does not have key"
        );
    }

    @Test(dependsOnMethods = "doubleTransactionTest")
    public void completeTransactionTest() {
        clientUnderTest.acceptCommand("TRANSACT 1 2 3", shutdownAtomic);

        for (var command : List.of("GET 2", "PUT 3 l", "PUT 1 y")) {
            var r = clientUnderTest.acceptCommand(command, shutdownAtomic);
            assertEquals(r, "Command acknowledged");
            assertFalse(clientUnderTest.isDone());
            assertFalse(globalKeyLock.isEmpty());
        }

        var commitResponse = clientUnderTest.acceptCommand("COMMIT", shutdownAtomic);
        assertTrue(commitResponse.startsWith("Successful command: "));
        assertTrue(clientUnderTest.isDone());

        assertTrue(btree.contains("3"));
        assertTrue(btree.contains("4"));

        var value = btree.get("3");

        assertTrue(value.isPresent());
        assertEquals(value.get(), "l");

        value = btree.get("1");

        assertTrue(value.isPresent());
        assertEquals(value.get(), "y");

        assertTrue(globalKeyLock.isEmpty());
    }

    @Test(dependsOnMethods = "doubleTransactionTest")
    public void otherClientLockTest() {
        var other = TreeClient.from(RANDOM.nextLong(), globalKeyLock, btree, null);
        other.acceptCommand("TRANSACT 1 2 3", shutdownAtomic);

        assertFalse(other.isDone());
        assertFalse(globalKeyLock.isEmpty());

        var failedTransactionResponse = clientUnderTest.acceptCommand("TRANSACT 1 2", shutdownAtomic);

        assertEquals(
                failedTransactionResponse,
                "Requested key(s) is locked"
        );

        other.acceptCommand("ABORT", shutdownAtomic);

        assertTrue(other.isDone());
        assertTrue(globalKeyLock.isEmpty());

        var transactionResponse = clientUnderTest.acceptCommand("TRANSACT 1 2", shutdownAtomic);

        assertTrue(transactionResponse.matches(EXPIRATION_DATE_REGEXP));
    }
}
