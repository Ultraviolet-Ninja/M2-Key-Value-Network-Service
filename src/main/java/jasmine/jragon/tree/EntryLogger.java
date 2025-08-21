package jasmine.jragon.tree;

import lombok.AccessLevel;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

final class EntryLogger {
    private static final Logger LOG = LoggerFactory.getLogger(EntryLogger.class.getName());

    private static final int DEFAULT_BUFFER = 10;

    private final boolean logging;
    private final String loggingFile;
    private final String[] buffer;
//    private final List<Future<Void>> futures;

    private int clock;
    @Setter(value = AccessLevel.PACKAGE)
    private boolean isReconstructing = false;

    EntryLogger(boolean logging, String loggingFile) {
        this.logging = logging;
        this.loggingFile = loggingFile;
        this.clock = 0;
        this.buffer = new String[DEFAULT_BUFFER];
//        this.futures = new ArrayList<>();
    }

    EntryLogger(int bufferSize, boolean logging, String loggingFile) {
        if (bufferSize <= 5) {
            throw new IllegalArgumentException("bufferSize must be greater than 0");
        }

        this.logging = logging;
        this.loggingFile = loggingFile;
        this.clock = 0;
        this.buffer = new String[bufferSize];
//        this.futures = new ArrayList<>();
    }

    void put(String key, String value) {
        if (!isReconstructing && logging) {
            buffer[clock++] = key + BTree.LOG_DELIMITER + value + '\n';

            if (clock == buffer.length) {
                clock = 0;
            }

            if (clock == 0) {
//                var copy = Arrays.copyOf(buffer, buffer.length);
//                var f = CompletableFuture.runAsync(() -> flushLogs(copy));
//                futures.add(f);
//                futures.removeIf(Future::isDone);
                flushImmediately();
            }
        }
    }

    private void flushLogs(String[] copy) {
        synchronized (loggingFile) {
            try (var out = new BufferedOutputStream(new FileOutputStream(loggingFile, true))) {
                for (var str : copy) {
                    out.write(str.getBytes());
                }
            } catch (IOException e) {
                LOG.error("Error while writing logging file with content: {}", Arrays.toString(copy), e);
            }
        }
    }

    void flushImmediately() {
        synchronized (loggingFile) {
            try (var out = new BufferedOutputStream(new FileOutputStream(loggingFile, true))) {
                for (var str : buffer) {
                    if (str != null) {
                        out.write(str.getBytes());
                    }
                }
                clock = 0;
            } catch (IOException e) {
                LOG.error("flushImmediately encountered an issue on {}", Arrays.toString(buffer), e);
            }
        }
    }
}
