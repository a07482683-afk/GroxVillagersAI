package dev.grox.ai;

import net.minecraft.nbt.NbtCompound;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GroxMemory {
    public final UUID id;
    public int bravery;
    public int trust;
    public int fear;
    public int mood;
    public int interactions;
    public long lastThreat;
    public final List<String> memories = new ArrayList<>();

    public GroxMemory(UUID id) {
        this.id = id;
        this.bravery = 20 + Math.floorMod(id.hashCode(), 81);
        this.trust = 35;
        this.fear = 0;
        this.mood = 50;
        this.interactions = 0;
    }

    public void remember(String text) {
        memories.add(text);
        while (memories.size() > 64) memories.remove(0);
    }

    public NbtCompound write() {
        NbtCompound n = new NbtCompound();
        n.putUuid("Id", id);
        n.putInt("Bravery", bravery);
        n.putInt("Trust", trust);
        n.putInt("Fear", fear);
        n.putInt("Mood", mood);
        n.putInt("Interactions", interactions);
        n.putLong("LastThreat", lastThreat);
        n.putInt("MemoryCount", memories.size());
        for (int i = 0; i < memories.size(); i++) n.putString("Memory" + i, memories.get(i));
        return n;
    }

    public static GroxMemory read(NbtCompound n) {
        UUID id = n.getUuid("Id");
        GroxMemory m = new GroxMemory(id);
        m.bravery = n.getInt("Bravery");
        m.trust = n.getInt("Trust");
        m.fear = n.getInt("Fear");
        m.mood = n.getInt("Mood");
        m.interactions = n.getInt("Interactions");
        m.lastThreat = n.getLong("LastThreat");
        int count = Math.min(64, n.getInt("MemoryCount"));
        for (int i = 0; i < count; i++) m.memories.add(n.getString("Memory" + i));
        return m;
    }
}