package jasmine.jragon.command;

import jasmine.jragon.tree.BTree;
import lombok.NonNull;

import java.util.Set;

public interface RequestHandler {
    default String handleRequest(@NonNull String[] arguments, @NonNull Set<String> lockSet, @NonNull BTree tree) {
        return "";
    }
}
