package dev.grox.ai;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GroxMemoryStore extends PersistentState {
    private final Map<UUID, GroxMemory> villagers = new HashMap<>();

    public GroxMemory get(UUID id) {
        return villagers.computeIfAbsent(id, GroxMemory::new);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound entries = new NbtCompound();
        for (Map.Entry<UUID, GroxMemory> e : villagers.entrySet()) {
            entries.put(e.getKey().toString(), e.getValue().write());
        }
        nbt.put("Villagers", entries);
        return nbt;
    }

    public static GroxMemoryStore fromNbt(NbtCompound nbt) {
        GroxMemoryStore store = new GroxMemoryStore();
        NbtCompound entries = nbt.getCompound("Villagers");
        for (String key : entries.getKeys()) {
            NbtCompound data = entries.getCompound(key);
            if (data.containsUuid("Id")) {
                GroxMemory memory = GroxMemory.read(data);
                store.villagers.put(memory.id, memory);
            }
        }
        return store;
    }
}
