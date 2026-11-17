package me.stella.antarctic.collection;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * A membership index that mirrors a {@code List} which may hold several {@code equals} elements: {@link #contains}
 * answers what {@code List.contains} answers, in O(1). Every {@link #add} must be paired with one {@link #removeOne}
 * when the element leaves the list.
 *
 * <p>Used by the world to mirror the current scheduled-tick batch so repeaters and comparators stop doing a linear
 * scan of up to 65,536 entries on every neighbor update. Not thread safe.
 */
public final class MultiplicitySet<E> {

    private final Object2IntOpenHashMap<E> counts = new Object2IntOpenHashMap<E>();

    public void add(E element) {
        this.counts.addTo(element, 1);
    }

    /** Drops one occurrence; the element stays a member while other equal occurrences remain. */
    public void removeOne(E element) {
        int previous = this.counts.addTo(element, -1);
        if (previous <= 1) {
            this.counts.removeInt(element);
        }
    }

    public boolean contains(Object element) {
        return this.counts.containsKey(element);
    }

    public int distinctSize() {
        return this.counts.size();
    }

    public boolean isEmpty() {
        return this.counts.isEmpty();
    }

    public void clear() {
        this.counts.clear();
    }
}
