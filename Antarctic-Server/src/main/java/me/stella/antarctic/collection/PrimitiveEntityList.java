package me.stella.antarctic.collection;

import java.util.Arrays;
import net.minecraft.server.Entity;

/**
 * Primitive-backed, low-allocation entity buffer inferred from Leaf / Gale.
 * Eliminates Iterator and ArrayList allocation overhead during high-frequency spatial queries.
 */
public final class PrimitiveEntityList {

    private Entity[] elements;
    private int size;

    public PrimitiveEntityList(int initialCapacity) {
        this.elements = new Entity[Math.max(initialCapacity, 16)];
        this.size = 0;
    }

    public PrimitiveEntityList() {
        this(32);
    }

    public void add(Entity entity) {
        if (this.size >= this.elements.length) {
            grow();
        }
        this.elements[this.size++] = entity;
    }

    public Entity get(int index) {
        if (index < 0 || index >= this.size) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + this.size);
        }
        return this.elements[index];
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public void clear() {
        Arrays.fill(this.elements, 0, this.size, null);
        this.size = 0;
    }

    public Entity[] rawArray() {
        return this.elements;
    }

    private void grow() {
        int newCapacity = this.elements.length + (this.elements.length >> 1);
        this.elements = Arrays.copyOf(this.elements, newCapacity);
    }
}
