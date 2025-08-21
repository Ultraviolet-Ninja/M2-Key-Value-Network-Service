package jasmine.jragon.command;

import jasmine.jragon.response.ServerResponse;
import jasmine.jragon.tree.BTree;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Set;

import static jasmine.jragon.command.ProtocolCommand.ABORT_TRANSACTION;
import static jasmine.jragon.command.ProtocolCommand.BEGIN_TRANSACTION;
import static jasmine.jragon.command.ProtocolCommand.CHECK;
import static jasmine.jragon.command.ProtocolCommand.COMMIT_TRANSACTION;
import static jasmine.jragon.command.ProtocolCommand.READ;
import static jasmine.jragon.command.ProtocolCommand.SHUTDOWN_SERVER;
import static jasmine.jragon.command.ProtocolCommand.UNSUPPORTED;
import static jasmine.jragon.command.ProtocolCommand.WRITE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("DataFlowIssue")
public class ProtocolCommandTest {
    private BTree bTree;

    @BeforeTest
    public void setup() {
        bTree = new BTree(5);
        bTree.put("a", "b");
        bTree.put("b", "c");
        bTree.put("c", "d");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void lookupNullTest() {
        ProtocolCommand.lookup(null);
    }

    @DataProvider
    public Object[][] lookupTestProvider() {
        return new Object[][] {
                {"", UNSUPPORTED},
                {"a", UNSUPPORTED},
                {"contain", UNSUPPORTED},
                {"shudown", UNSUPPORTED},
                {"put", WRITE},
                {"get", READ},
                {"coNtAins", CHECK},
                {"Transact", BEGIN_TRANSACTION},
                {"COMMIT", COMMIT_TRANSACTION},
                {"abort", ABORT_TRANSACTION},
        };
    }

    @Test(dataProvider = "lookupTestProvider")
    public void lookupTest(String incomingText, ProtocolCommand expectedCommand) {
        assertEquals(ProtocolCommand.lookup(incomingText), expectedCommand);
    }

    @DataProvider
    public Object[][] argumentCountTestProvider() {
        return new Object[][]{
                {READ, -1, false},
                {READ, 0, false},
                {READ, 1, true},
                {READ, 2, false},
                {WRITE, -1, false},
                {WRITE, 0, false},
                {WRITE, 2, true},
                {WRITE, 1, false},
                {WRITE, 7, false},
                {CHECK, -1, false},
                {CHECK, 0, false},
                {CHECK, 1, true},
                {CHECK, 2, false},
                {CHECK, 7, false},
                {BEGIN_TRANSACTION, Integer.MIN_VALUE, false},
                {BEGIN_TRANSACTION, -1, false},
                {BEGIN_TRANSACTION, 0, false},
                {BEGIN_TRANSACTION, 1, true},
                {BEGIN_TRANSACTION, Integer.MAX_VALUE, true},
                {COMMIT_TRANSACTION, -1, false},
                {COMMIT_TRANSACTION, 0, true},
                {COMMIT_TRANSACTION, 1, false},
                {COMMIT_TRANSACTION, Integer.MAX_VALUE, false},
                {ABORT_TRANSACTION, -1, false},
                {ABORT_TRANSACTION, 0, true},
                {ABORT_TRANSACTION, 1, false},
                {ABORT_TRANSACTION, Integer.MAX_VALUE, false},
                {SHUTDOWN_SERVER, -1, false},
                {SHUTDOWN_SERVER, 0, true},
                {SHUTDOWN_SERVER, 1, false},
                {SHUTDOWN_SERVER, Integer.MAX_VALUE, false},
        };
    }

    @Test(dataProvider = "argumentCountTestProvider")
    public void argumentCountTest(ProtocolCommand protocolCommand,
                                  int argumentCount, boolean expectedResult) {
        if (expectedResult) {
            assertTrue(protocolCommand.containsSufficientArguments(argumentCount));
        } else {
            assertFalse(protocolCommand.containsSufficientArguments(argumentCount));
        }
    }

    @DataProvider
    public Object[][] isTransactionTestProvider() {
        return new Object[][]{
                {READ, true},
                {WRITE, true},
                {CHECK, true},
                {BEGIN_TRANSACTION, false},
                {COMMIT_TRANSACTION,  false},
                {ABORT_TRANSACTION, false},
                {SHUTDOWN_SERVER, false},
        };
    }

    @Test(dataProvider = "isTransactionTestProvider")
    public void isTransactionTest(ProtocolCommand protocolCommand,
                                  boolean expectedResult) {
        if (expectedResult) {
            assertTrue(protocolCommand.isTransactionCommand());
        } else {
            assertFalse(protocolCommand.isTransactionCommand());
        }
    }

    @DataProvider
    public Object[][] isWALTestProvider() {
        return new Object[][]{
                {READ, false},
                {WRITE, true},
                {CHECK, false},
                {BEGIN_TRANSACTION, true},
                {COMMIT_TRANSACTION, true},
                {ABORT_TRANSACTION, true},
                {SHUTDOWN_SERVER, false},
        };
    }

    @Test(dataProvider = "isWALTestProvider")
    public void isWALTest(ProtocolCommand protocolCommand,
                                  boolean expectedResult) {
        if (expectedResult) {
            assertTrue(protocolCommand.isWriteAhead());
        } else {
            assertFalse(protocolCommand.isWriteAhead());
        }
    }

    @DataProvider
    public Object[][] handleRequestNullTest() {
        return new Object[][] {
                {READ, null, null, null},
                {READ, new String[]{}, null, null},
                {READ, new String[]{}, Collections.emptySet(), null},
                {WRITE, null, null, null},
                {WRITE, new String[]{}, null, null},
                {WRITE, new String[]{}, Collections.emptySet(), null},
                {CHECK, null, null, null},
                {CHECK, new String[]{}, null, null},
                {CHECK, new String[]{}, Collections.emptySet(), null},
                {ABORT_TRANSACTION, null, null, null},
                {ABORT_TRANSACTION, new String[]{}, null, null},
                {ABORT_TRANSACTION, new String[]{}, Collections.emptySet(), null},
                {UNSUPPORTED, null, null, null},
                {UNSUPPORTED, new String[]{}, null, null},
                {UNSUPPORTED, new String[]{}, Collections.emptySet(), null},
        };
    }

    @Test(
            dataProvider = "handleRequestNullTest",
            expectedExceptions = NullPointerException.class
    )
    public void handleRequestTest(ProtocolCommand protocolCommand, String[] arguments,
                                  Set<String> lockSet, BTree tree) {
        protocolCommand.handleRequest(arguments, lockSet, tree);
    }

    @DataProvider
    public Object[][] readCommandTestProvider() {
        return new Object[][] {
                {new String[]{}, Collections.emptySet(), ServerResponse.INSUFFICIENT_ARGUMENTS.toString()},
                {new String[]{"a", "b"}, Collections.emptySet(), ServerResponse.INSUFFICIENT_ARGUMENTS.toString()},
                {new String[]{"a"}, Collections.emptySet(), "b"},
                {new String[]{"a"}, Set.of("a", "b"), ServerResponse.KEY_LOCKED_ISSUE.toString()},
                {new String[]{"a"}, Set.of("b"), "b"},
                {new String[]{"d"}, Set.of("b"), "null"},
        };
    }

    @Test(dataProvider = "readCommandTestProvider")
    public void readCommandTest(String[] arguments, Set<String> lockSet, String expected) {
        var response = READ.handleRequest(arguments, lockSet, bTree);

        assertEquals(response, expected);
    }

    @DataProvider
    public Object[][] writeCommandTestProvider() {
        return new Object[][] {
                {new String[]{}, Collections.emptySet(), ServerResponse.INSUFFICIENT_ARGUMENTS.toString()},
                {new String[]{"a", "b"}, Collections.emptySet(), "b"},
                {new String[]{"a"}, Collections.emptySet(), ServerResponse.INSUFFICIENT_ARGUMENTS.toString()},
                {new String[]{"a", "b", "c"}, Collections.emptySet(), ServerResponse.INSUFFICIENT_ARGUMENTS.toString()},
                {new String[]{"a", "r"}, Set.of("a", "b"), ServerResponse.KEY_LOCKED_ISSUE.toString()},
                {new String[]{"a", "r"}, Set.of("b"), "b"},
                {new String[]{"d", "e"}, Set.of("b"), "null"},
        };
    }

    @Test(dataProvider = "writeCommandTestProvider")
    public void writeCommandTest(String[] arguments, Set<String> lockSet, String expected) {
        var response = ProtocolCommand.WRITE.handleRequest(arguments, lockSet, bTree);

        assertEquals(response, expected);
    }

    @DataProvider
    public Object[][] containsCommandTestProvider() {
        return new Object[][] {
                {new String[]{}, Collections.emptySet(), ServerResponse.INSUFFICIENT_ARGUMENTS.toString()},
                {new String[]{"a", "b"}, Collections.emptySet(), ServerResponse.INSUFFICIENT_ARGUMENTS.toString()},
                {new String[]{"a"}, Collections.emptySet(), "true"},
                {new String[]{"a", "b", "c"}, Collections.emptySet(), ServerResponse.INSUFFICIENT_ARGUMENTS.toString()},
                {new String[]{"a"}, Set.of("a", "b"), ServerResponse.KEY_LOCKED_ISSUE.toString()},
                {new String[]{"r"}, Set.of("b"), "false"},
        };
    }

    @Test(dataProvider = "containsCommandTestProvider")
    public void containsCommandTest(String[] arguments, Set<String> lockSet, String expected) {
        var response = ProtocolCommand.CHECK.handleRequest(arguments, lockSet, bTree);

        assertEquals(response, expected);
    }

    @DataProvider
    public Object[][] otherCommandTestProvider() {
        return new Object[][] {
                {BEGIN_TRANSACTION, ""},
                {COMMIT_TRANSACTION, ""},
                {ABORT_TRANSACTION, ""},
                {SHUTDOWN_SERVER, ""},
                {UNSUPPORTED, "Unsupported command"}
        };
    }

    @Test(dataProvider = "otherCommandTestProvider")
    public void otherCommandTest(ProtocolCommand protocol, String expectedResponse) {
        assertEquals(
                protocol.handleRequest(new String[]{}, Collections.emptySet(), bTree),
                expectedResponse
        );
    }
}
