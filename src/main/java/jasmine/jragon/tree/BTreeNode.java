package jasmine.jragon.tree;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

final class BTreeNode {
    final BTreeNode[] children;
    private final KVPair[] pairs;
    private final int degree;

    @Getter
    private int pairCount;
    private int childCount;

    BTreeNode(int degree) {
        if (degree < BTree.MIN_DEGREE) {
            throw new IllegalArgumentException("Degree must be greater than or equal to 2");
        }

        pairs = new KVPair[2 * degree - 1];
        children = new BTreeNode[2 * degree];
        this.degree = degree;
        pairCount = 0;
        childCount = 0;
    }

    String insert(@NonNull String key, @NonNull String value) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }

        var pair = new KVPair(key, value);
        int index = findLocation(pair);

        if (index >= 0 || isLeaf()) {
            return handleSimpleInsert(pair);
        } else {
            index = -index - 1;

            if (children[index].isFull()) {
                splitChild(index, children[index]);

                if (pairs[index].compareTo(key) < 0)
                    index++;
            }

            return children[index].insert(key, value);
        }
    }

    private String handleSimpleInsert(KVPair pair) {
        if (pairCount == 0) {
            pairs[pairCount++] = pair;
            return null;
        }

        int index = findLocation(pair);

        if (index >= 0) {
            var old = pairs[index].value;
            pairs[index].value = pair.value;
            return old;
        }

        int dest = -index - 1;
        for (int i = pairCount; i > dest; i--) {
            pairs[i] = pairs[i - 1];
        }
        pairs[dest] = pair;
        pairCount++;
        return null;
    }

    int findLocation(@NonNull String key) {
        return findLocation(new KVPair(key));
    }

    private int findLocation(KVPair pair) {
        return Arrays.binarySearch(pairs, pair, KVPair.SEARCH_PARAMETER);
    }

    void splitChild(int i, @NonNull BTreeNode child) {
        var split = new BTreeNode(child.degree);
        split.pairCount = degree - 1;

        System.arraycopy(child.pairs, degree, split.pairs, 0, degree - 1);

        if (!child.isLeaf()) {
            System.arraycopy(child.children, degree, split.children, 0, degree);
            Arrays.fill(child.children, degree, child.children.length, null);

            split.updateChildCount();
            child.updateChildCount();
        }

        child.pairCount = degree - 1;

        for (int j = pairCount; j >= i + 1; j--)
            children[j + 1] = children[j];

        children[i + 1] = split;
        updateChildCount();

        for (int j = pairCount - 1; j >= i; j--)
            pairs[j + 1] = pairs[j];

        pairs[i] = child.pairs[degree - 1];
        pairCount++;
        Arrays.fill(child.pairs, degree - 1, child.pairs.length, null);
    }

    String search(@NonNull String key) {
        if (pairCount == 0)
            return null;

        int index = findLocation(new KVPair(key));

        if (index >= 0)
            return pairs[index].value;

        if (isLeaf())
            return null;

        int dest = -index - 1;

        return children[dest].search(key);
    }

    boolean isLeaf() {
        return childCount == 0;
    }

    boolean isFull() {
        return pairs.length == pairCount;
    }

    @Override
    public String toString() {
        return String.format("Nodes %s - Child Count: %d - Pairs: %d", Arrays.toString(pairs), childCount, pairCount);
    }

    long getKeyCount() {
        return getKeyCount(this);
    }

    private long getKeyCount(BTreeNode node) {
        if (node == null)
            return 0;

        Stream.Builder<String> builder = Stream.builder();
        extractKeysFromChildren(node, builder);

        return builder.build()
                .distinct()
                .count();
    }

    private void extractKeysFromChildren(BTreeNode node, Stream.Builder<String> builder) {
        if (node != null) {
            Arrays.stream(node.pairs)
                    .takeWhile(Objects::nonNull)
                    .map(KVPair::getKey)
                    .forEach(builder);

            for (BTreeNode child : node.children) {
                extractKeysFromChildren(child, builder);
            }
        }
    }

    int getNodeCount() {
        return getNodeCount(this);
    }

    private int getNodeCount(BTreeNode node) {
        if (node == null)
            return 0;

        int n = 1;
        for (BTreeNode child : node.children) {
            n += getNodeCount(child);
        }

        return n;
    }

    private void updateChildCount() {
        childCount = Math.toIntExact(Arrays.stream(children)
                .takeWhile(Objects::nonNull)
                .count());
    }

    private static final class KVPair implements Comparable<KVPair> {
        private static final Comparator<KVPair> SEARCH_PARAMETER = Comparator.nullsLast(KVPair::compareTo);

        @Getter(value = AccessLevel.PRIVATE)
        private final String key;
        private String value;

        public KVPair(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public KVPair(String key) {
            this.key = key;
            this.value = "";
        }

        @Override
        public int compareTo(KVPair o) {
            return key.compareTo(o.key);
        }

        public int compareTo(String key) {
            return this.key.compareTo(key);
        }

        @Override
        public String toString() {
            return key + " -> " + value;
        }
    }
}
