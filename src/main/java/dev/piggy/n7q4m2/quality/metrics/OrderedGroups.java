package dev.piggy.n7q4m2.quality.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

/** Only neighbouring entries merge, so an A/B/A selection order stays A/B/A. */
public final class OrderedGroups {
    private OrderedGroups() {}
    public record Group<T>(T representative, int count) {}
    public static <T> List<Group<T>> group(List<T> entries, BiPredicate<T, T> same, ToIntFunction<T> count) {
        List<Group<T>> result = new ArrayList<>();
        for (T entry : entries) {
            int amount = count.applyAsInt(entry);
            if (amount <= 0) continue;
            if (!result.isEmpty() && same.test(result.getLast().representative(), entry)) {
                Group<T> old = result.removeLast();
                result.add(new Group<>(old.representative(), old.count() + amount));
            } else result.add(new Group<>(entry, amount));
        }
        return List.copyOf(result);
    }
}
