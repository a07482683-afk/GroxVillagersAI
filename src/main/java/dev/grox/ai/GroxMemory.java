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
    public int social;
    public int curiosity;
    public long lastThreat;
    // Expression state is stored now; the renderer will display it in a later visual stage.
    public String expression = "neutral";
    public int expressionTicks = 0;
    public final List<String> memories = new ArrayList<>();

    public GroxMemory(UUID id) {
        this.id = id;
        this.bravery = 20 + Math.floorMod(id.hashCode(), 81);
        this.trust = 35;
        this.fear = 0;
        this.mood = 50;
        this.interactions = 0;
        this.social = 35;
        this.curiosity = 40 + Math.floorMod(id.hashCode() * 31, 61);
    }

    public void setExpression(String value, int ticks) {
        this.expression = value;
        this.expressionTicks = Math.max(0, ticks);
    }

    public void updateExpression() {
        if (expressionTicks > 0) {
            expressionTicks--;
            return;
        }
        if (fear >= 60) expression = "afraid";
        else if (trust <= 12 && mood <= 25) expression = "angry";
        else if (mood <= 25) expression = "sad";
        else if (mood >= 75) expression = "happy";
        else expression = "neutral";
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
        n.putInt("Social", social);
        n.putInt("Curiosity", curiosity);
        n.putLong("LastThreat", lastThreat);
        n.putString("Expression", expression);
        n.putInt("ExpressionTicks", expressionTicks);
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
        m.social = n.contains("Social") ? n.getInt("Social") : 35;
        m.curiosity = n.contains("Curiosity") ? n.getInt("Curiosity") : 50;
        m.lastThreat = n.getLong("LastThreat");
        m.expression = n.contains("Expression") ? n.getString("Expression") : "neutral";
        m.expressionTicks = n.contains("ExpressionTicks") ? Math.max(0, n.getInt("ExpressionTicks")) : 0;
        int count = Math.min(64, n.getInt("MemoryCount"));
        for (int i = 0; i < count; i++) m.memories.add(n.getString("Memory" + i));
        return m;
    }
}
