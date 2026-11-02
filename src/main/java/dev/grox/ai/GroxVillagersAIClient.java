package dev.grox.ai;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.PacketByteBufs;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class GroxVillagersAIClient implements ClientModInitializer {
    private KeyBinding stopAndTalkKey;

    @Override
    public void onInitializeClient() {
        stopAndTalkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.groxvillagersai.stop_and_talk",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F,
                "category.groxvillagersai"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (stopAndTalkKey.wasPressed()) {
                if (client.player == null || client.world == null) continue;
                if (client.crosshairTarget instanceof EntityHitResult hit
                        && hit.getEntity() instanceof VillagerEntity villager
                        && client.player.squaredDistanceTo(villager) <= 36.0) {
                    PacketByteBuf packet = PacketByteBufs.create();
                    packet.writeUuid(villager.getUuid());
                    ClientPlayNetworking.send(GroxVillagersAI.STOP_REQUEST, packet);
                }
            }
        });
    }
}
