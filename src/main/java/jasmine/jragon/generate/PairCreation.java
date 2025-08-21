package jasmine.jragon.generate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

public class PairCreation {
    public static final String OUTPUT_FILE = "1-million-pairs.txt";

    private static final String TEXT_FILE = "words.txt";
    private static final int NUMBER_OF_PAIRS = 1_000_000;
    private static final Logger LOG = LoggerFactory.getLogger(PairCreation.class);

    public static void main(String[] args) {
        List<String> words;
        var rand = new Random();
        try (var wordReader = new BufferedReader(new InputStreamReader(PairCreation.class.getResourceAsStream(TEXT_FILE)))) {
            words = wordReader.lines()
                    .map(String::toLowerCase)
                    .mapMulti((String word, Consumer<String> c) -> {
                        var firstRand = String.valueOf((char) ('a' + rand.nextInt(26)));
                        var secondRand = String.valueOf((char) ('a' + rand.nextInt(26)));

                        c.accept(word);
                        c.accept(word + firstRand);
                        c.accept(word + firstRand + secondRand);
                    })
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Set<String> usedKeys = new HashSet<>(NUMBER_OF_PAIRS << 1);

        int n = words.size();
        try (var out = new BufferedWriter(new FileWriter(OUTPUT_FILE))) {
            for (int i = 0; usedKeys.size() < NUMBER_OF_PAIRS;) {
                var w1 = words.get(rand.nextInt(n));
                var w2 = words.get(rand.nextInt(n));

                while (w1.equals(w2)) {
                    w2 = words.get(rand.nextInt(n));
                }

                if (!usedKeys.contains(w1)) {
                    usedKeys.add(w1);
                    out.write(w1 + "=" + w2 + "\n");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOG.trace("1 Million word pairs created");
    }
}