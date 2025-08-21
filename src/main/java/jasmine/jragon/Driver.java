package jasmine.jragon;

import jasmine.jragon.benchmark.GauntletTester;
import jasmine.jragon.generate.PairCreation;

public final class Driver {
    public static void main(String[] args) {
        if (args.length > 0) {
            var first = args[0].toLowerCase();
            switch (first) {
                case "-c", "--client" -> Client.main(args);
                case "-pc" -> PairCreation.main(args);
                case "-g" -> GauntletTester.main(args);
                default -> Server.main(args);
            }
            return;
        }
        Server.main(args);
    }
}
