package jasmine.jragon;

import jasmine.jragon.client.TreeClient;
import jasmine.jragon.generate.PairCreation;
import jasmine.jragon.response.ServerResponse;
import jasmine.jragon.tree.BTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

public final class Server {
    public static final int PORT = 8080;

    private static final int BUFFER_SIZE = 1024;
    private static final LongAdder CLIENT_COUNTER = new LongAdder();
    private static final AtomicBoolean IS_SERVER_SHUTDOWN = new AtomicBoolean(false);
    private static final Map<SelectableChannel, TreeClient> CONNECTION_MAP = new HashMap<>();
    private static final Set<String> GLOBAL_KEY_LOCK = new HashSet<>();

    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    private static BTree serverTree;
    private static final String RECONSTRUCTION_FILE = "tree-log.txt";

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("-load-mil")) {
            if (!new File(PairCreation.OUTPUT_FILE).exists()) {
                PairCreation.main(args);
            }

            serverTree = new BTree(5, new File(PairCreation.OUTPUT_FILE));
        } else {
            serverTree = new BTree(5, new File(RECONSTRUCTION_FILE));
        }

        try {
            startServer(args);
        } catch (IOException e) {
            LOG.error("Internal Error Occurred: ", e);
        } finally {
            serverTree.shutdownGracefully();
        }
    }

    private static void startServer(String[] args) throws IOException {
        LOG.trace("Starting Server");
        try (var selector = Selector.open();
             var server = ServerSocketChannel.open()) {

            int port = PORT;
            try {
                if (args.length > 2) {
                    port = Integer.parseInt(args[2]);
                } else if (args.length > 1) {
                    port = Integer.parseInt(args[1]);
                }
            } catch (NumberFormatException e) {
                LOG.warn("Invalid port number given. Using 8080 as default");
            }

            server.bind(new InetSocketAddress(port));
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);

            while (isRunning()) {
                if (selector.select() != 0) {
                    for (var selectionKey : selector.selectedKeys()) {
                        if (selectionKey.isAcceptable()) {
                            acceptIncoming(selectionKey.channel(), selector);
                        } else if (selectionKey.isReadable()) {
                            var client = selectionKey.channel();
                            try {
                                readIncomingCommand(client);
                            } catch (IOException e) {
                                LOG.error("Unexpected Drop of connection: {}", e.getMessage());
                                removeClient(client);
                            }
                        }
                    }

                    selector.selectedKeys().clear();
                }
            }
        }
    }

    private static void acceptIncoming(SelectableChannel acceptedChannel, Selector selector)
            throws IOException {
        if (acceptedChannel instanceof ServerSocketChannel channel) {
            var client = channel.accept();
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);

            var treeClient = TreeClient.from(
                    CLIENT_COUNTER.longValue(),
                    GLOBAL_KEY_LOCK,
                    serverTree,
                    client
            );
            CONNECTION_MAP.put(client, treeClient);
            System.out.println("Accepted connection from " + treeClient);

            CLIENT_COUNTER.increment();
        }
    }

    private static void readIncomingCommand(SelectableChannel incomingChannel)
            throws IOException {
        if (incomingChannel instanceof SocketChannel client) {
            var buffer = ByteBuffer.allocate(BUFFER_SIZE);
            int n = client.read(buffer);

            var response = "";
            if (n == -1) {
                removeClient(client);
                LOG.trace("Client closed");
                return;
            } else if (!CONNECTION_MAP.containsKey(client)) {
                LOG.warn("Client not found");
                response = ServerResponse.UNKNOWN_CLIENT.toString();
            } else {
                buffer.flip();
                var request = new String(buffer.array(), buffer.position(), n)
                        .trim();

                response = CONNECTION_MAP.get(client)
                        .acceptCommand(request, IS_SERVER_SHUTDOWN);
            }

            buffer.clear();
            buffer.put(response.getBytes());
            buffer.flip();

            while (buffer.hasRemaining()) {
                client.write(buffer);
            }
        }
    }

    private static void removeClient(SelectableChannel client) throws IOException {
        var internalClient = CONNECTION_MAP.remove(client);
        client.close();

        if (!internalClient.isDone()) {
            internalClient.eraseTransaction();
        }
    }

    private static boolean isRunning() {
        return !(IS_SERVER_SHUTDOWN.get() &&
                CONNECTION_MAP.values()
                        .stream()
                        .allMatch(TreeClient::isDone));
    }
}