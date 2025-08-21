package jasmine.jragon.benchmark;

import jasmine.jragon.Server;
import jasmine.jragon.command.ProtocolCommand;
import jasmine.jragon.generate.PairCreation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static jasmine.jragon.Client.DEFAULT_HOST;

public final class GauntletTester {
    private static final Logger LOG = LoggerFactory.getLogger(GauntletTester.class);
    private static final Scanner USER = new Scanner(System.in);
    private static final Map<Integer, DoubleSummaryStatistics> READ_STATS = new HashMap<>();
    private static final int ITERATION_COUNT = 3;

    public static void main(String[] args) {
        LOG.debug("Running the Gauntlet");
        var concurrentClientCounts = List.of(5);
        var keys = getKeys();

        if (keys.isEmpty())
            return;

        for (int count : concurrentClientCounts) {
            READ_STATS.putIfAbsent(count, new DoubleSummaryStatistics());
            for (int i = 0; i < ITERATION_COUNT; i++) {
                LOG.debug("Client Count {} - Iteration {}", count, i+1);
                var stats = conductTrial(count, keys, args);

                READ_STATS.get(count).combine(stats);
            }
        }

        LOG.debug("Read Stats {}", READ_STATS);
    }

    private static List<String> getKeys() {
        try (var reader = new BufferedReader(new FileReader(PairCreation.OUTPUT_FILE))) {
            return reader.lines()
                    .map(l -> l.substring(0, l.indexOf('=')))
                    .toList();
        } catch (IOException e) {
            LOG.error(e.getMessage());
            return Collections.emptyList();
        }
    }

    private static DoubleSummaryStatistics conductTrial(int count, List<String> keys, String[] args) {
        int[] counter = {0};
        var clients = IntStream.range(0, count)
                .mapToObj(ignored -> {
                    try {
                        return createSocketChannel(args);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(s -> new ClientRunner(counter[0]++, s, keys))
                .toList();

        var executor = Executors.newCachedThreadPool();

        List<Future<DoubleSummaryStatistics>> futures = new ArrayList<>();
        try {
            futures = executor.invokeAll(clients);

//            executor.awaitTermination(1, TimeUnit.MINUTES);
            USER.nextLine();
        } catch (InterruptedException e) {
            LOG.error(e.getMessage());
        }

        for (var client : clients) {
            try {
                client.close();
            } catch (IOException e) {
                LOG.warn("Error closing socket channel", e);
            }
        }

        var fullStats = futures.stream()
                .map(f -> {
            try {
                return f.get();
            } catch (InterruptedException | ExecutionException e) {
                return null;
            }
        })
                .filter(Objects::nonNull)
                .reduce(new DoubleSummaryStatistics(), (a, b) -> {a.combine(b); return a; });

        LOG.debug("Run Stats {}", fullStats);

        executor.shutdown();

        return fullStats;
    }

    private static SocketChannel createSocketChannel(String[] args) throws IOException {
        var s = SocketChannel.open();

        var ipAddress = args.length > 1 ? args[1] : DEFAULT_HOST;
        s.connect(new InetSocketAddress(ipAddress, Server.PORT));
        s.configureBlocking(true);

        return s;
    }

    private record ClientRunner(int id,
                                SocketChannel socketChannel,
                                List<String> keys) implements AutoCloseable, Callable<DoubleSummaryStatistics> {
        private static final int LOOP_COUNT = 10_000;
        private static final int SLEEP_AMOUNT = 20;

        @Override
        public DoubleSummaryStatistics call() {
            var summaryStatistics = new DoubleSummaryStatistics();
            var rand = new Random();
            int size = keys.size();
            var buffer = ByteBuffer.allocate(1024);
            long start = System.currentTimeMillis() / 1000;

            for (int i = 0; i < LOOP_COUNT; i++) {
                buffer.clear()
                        .put(ProtocolCommand.READ.getCommandName().getBytes())
                        .put(" ".getBytes())
                        .put(keys.get(rand.nextInt(size)).getBytes())
                        .flip();

                while (buffer.hasRemaining()) {
                    try {
                        socketChannel.write(buffer);
                    } catch (IOException e) {
                        LOG.error("Runner {} encountered an error while writing to channel", id, e);
                    }
                }

                buffer.clear();

                int n;
                try {
                    n = socketChannel.read(buffer);
                    buffer.flip();

                    String response = new String(buffer.array(), buffer.position(), n);
                    LOG.trace("Runner {} - Server said: {}", id, response);
                } catch (IOException e) {
                    LOG.error("Runner {} encountered an error while reading from the channel", id, e);
                }

                try {
                    Thread.sleep(SLEEP_AMOUNT);
                } catch (InterruptedException e) {
                    LOG.error("Runner {} Sleep interrupted", id, e);
                }
            }

            long end = System.currentTimeMillis() / 1000;
            LOG.debug("Runner {} finished", id);
            summaryStatistics.accept((double) LOOP_COUNT / (end - start));

            return summaryStatistics;
        }

        @Override
        public void close() throws IOException {
            LOG.debug("Closing Runner {}", id);
            socketChannel.close();
        }
    }
}
