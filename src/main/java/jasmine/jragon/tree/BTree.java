package jasmine.jragon.tree;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

public final class BTree {
    public static final String LOG_DELIMITER = "=";

    static final int MIN_DEGREE = 2;

    private static final Logger LOG = LoggerFactory.getLogger(BTree.class);

    private final int degree;
    private final EntryLogger logger;

    private BTreeNode root;

    public BTree(int degree) {
        validateDegree(degree);
        this.degree = degree;
        root = null;
        logger = new EntryLogger(false, "");
    }

    public BTree(int degree, @NonNull String loggingFileName) {
        validateDegree(degree);
        this.degree = degree;
        root = null;
        logger = new EntryLogger(true, loggingFileName);
    }

    public BTree(int degree, @NonNull File reconstructionFile) {
        validateDegree(degree);
        if (!reconstructionFile.exists() ||  !reconstructionFile.isFile()) {
            throw new IllegalArgumentException(reconstructionFile.getAbsolutePath() + " is not a valid file");
        }

        this.degree = degree;
        logger = new EntryLogger(true, reconstructionFile.getName());
        logger.setReconstructing(true);
        reconstruct(reconstructionFile);
        logger.setReconstructing(false);
    }

    public BTree(int degree, @NonNull String loggingFileName, int bufferSize) {
        validateDegree(degree);
        this.degree = degree;
        root = null;
        logger = new EntryLogger(bufferSize, true, loggingFileName);
    }

    public BTree(int degree, @NonNull File reconstructionFile, int bufferSize) {
        validateDegree(degree);
        if (!reconstructionFile.exists() ||  !reconstructionFile.isFile()) {
            throw new IllegalArgumentException(reconstructionFile.getAbsolutePath() + " is not a valid file");
        }

        this.degree = degree;
        logger = new EntryLogger(bufferSize, true, reconstructionFile.getName());
        logger.setReconstructing(true);
        reconstruct(reconstructionFile);
        logger.setReconstructing(false);
    }

    public String put(@NonNull String key, @NonNull String value) {
        if (root == null) {
            root = new BTreeNode(degree);
        }

        if (!root.isFull()) {
            var old = root.insert(key, value);
            logger.put(key, value);
            return old;
        }

        var nextRoot = new BTreeNode(degree);
        nextRoot.children[0] = root;
        nextRoot.splitChild(0, root);

        int i = nextRoot.findLocation(key);

        if (i < 0)
            i = -i - 1;

        var v = nextRoot.children[i].insert(key, value);
        logger.put(key, value);
        root = nextRoot;

        return v;
    }

    public Optional<String> get(@NonNull String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Key is empty");
        }

        return root != null ?
                Optional.ofNullable(root.search(key)) :
                Optional.empty();
    }

    public boolean contains(@NonNull String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Key is empty");
        }

        return root != null && root.search(key) != null;
    }

    public long keyCount() {
        return root == null ? 0 : root.getKeyCount();
    }

    public int nodeCount() {
        return root == null ? 0 : root.getNodeCount();
    }

    public void shutdownGracefully() {
        logger.flushImmediately();
    }

    private void reconstruct(File file) {
        if (file.exists() && file.isFile()) {
            try (var lines = new BufferedReader(new FileReader(file)).lines()) {
                lines.map(line -> line.split(LOG_DELIMITER))
                        .filter(line -> {
                if (line.length != 2) {
                    LOG.warn("{} is an anomaly", Arrays.toString(line));
                    return false;
                }
                return true;
            }).forEach(line -> put(line[0], line[1]));
            } catch (IOException e) {
                LOG.error("Error reading file {}. Cannot reconstruct", file.getAbsolutePath(), e);
            }
        } else {
            LOG.warn("{} is not a valid file. Reconstruction Aborted", file.getAbsolutePath());
        }
    }

    private static void validateDegree(int degree) {
        if (degree < MIN_DEGREE)
            throw new IllegalArgumentException("Degree must be >= " + MIN_DEGREE);
    }
}
