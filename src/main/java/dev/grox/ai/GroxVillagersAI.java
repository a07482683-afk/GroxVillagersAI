package dev.grox.ai;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class GroxVillagersAI implements ModInitializer {
    private static int tickCounter = 0;

    @Override
    public void onInitialize() {
        // ЛКМ начинает разговор. Shift + ЛКМ показывает характеристики только в Creative.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || !(entity instanceof VillagerEntity villager)
                    || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            if (serverPlayer.isSneaking()) {
                if (!serverPlayer.isCreative()) return ActionResult.PASS;
                sendStats(serverPlayer, memory((ServerWorld) world, villager));
                return ActionResult.SUCCESS;
            }

            ServerWorld serverWorld = (ServerWorld) world;
            GroxMemory m = memory(serverWorld, villager);
            m.interactions++;
            m.remember("Игрок подошёл и начал разговор.");
            m.trust = Math.min(100, m.trust + 1);
            markStoreDirty(serverWorld);
            String answer = GroxDialogue.greeting(m, villager);
            serverPlayer.sendMessage(Text.literal("§6[" + villager.getName().getString()
                    + "] §f" + answer), false);
            return ActionResult.SUCCESS;
        });

        // ПКМ оставлен Minecraft: обычная торговля не перехватывается.
        // Shift + ПКМ показывает параметры только в Creative.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(entity instanceof VillagerEntity villager)
                    || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            if (!serverPlayer.isSneaking() || !serverPlayer.isCreative()) {
                return ActionResult.PASS;
            }
            sendStats(serverPlayer, memory((ServerWorld) world, villager));
            return ActionResult.SUCCESS;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(literal("grox")
                .then(argument("message", StringArgumentType.greedyString()).executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    ServerWorld world = player.getServerWorld();
                    List<VillagerEntity> nearby = world.getEntitiesByClass(VillagerEntity.class,
                            player.getBoundingBox().expand(16), v -> v.isAlive());
                    if (nearby.isEmpty()) {
                        context.getSource().sendFeedback(() -> Text.literal("§cРядом нет жителей."), false);
                        return 0;
                    }

                    VillagerEntity villager = nearby.get(0);
                    GroxMemory m = memory(world, villager);
                    String message = StringArgumentType.getString(context, "message");
                    m.remember("Игрок: " + message);
                    m.interactions++;
                    updateRelationship(message, m);
                    markStoreDirty(world);
                    String answer = GroxDialogue.answer(message, m, villager);
                    context.getSource().sendFeedback(() -> Text.literal("§6["
                            + villager.getName().getString() + "] §f" + answer), false);
                    return 1;
                }))
            )
        );

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (++tickCounter % 40 != 0) return;
            for (ServerPlayerEntity player : world.getPlayers()) {
                Box area = player.getBoundingBox().expand(48);
                for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class,
                        area, v -> v.isAlive())) {
                    think(world, villager);
                }
            }
        });
    }

    private static void updateRelationship(String message, GroxMemory m) {
        String s = message.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("спасибо") || s.contains("помог") || s.contains("друг")
                || s.contains("привет") || s.contains("здравств")) {
            m.trust = Math.min(100, m.trust + 3);
            m.mood = Math.min(100, m.mood + 4);
            m.fear = Math.max(0, m.fear - 2);
        } else if (s.contains("угрож") || s.contains("убью") || s.contains("ненавиж")) {
            m.trust = Math.max(0, m.trust - 8);
            m.mood = Math.max(0, m.mood - 8);
            m.fear = Math.min(100, m.fear + 8);
        } else {
            m.mood = Math.min(100, m.mood + 1);
        }
    }

    private static void sendStats(ServerPlayerEntity player, GroxMemory memory) {
        player.sendMessage(Text.literal("§6[Грокс] §fСмелость: " + memory.bravery
                + " | Доверие: " + memory.trust + " | Страх: " + memory.fear
                + " | Настроение: " + memory.mood
                + " | Разговоров: " + memory.interactions), false);
    }

    private static GroxMemory memory(ServerWorld world, VillagerEntity villager) {
        GroxMemoryStore store = world.getPersistentStateManager().getOrCreate(
                GroxMemoryStore::fromNbt, GroxMemoryStore::new, "grox_villager_memories");
        return store.get(villager.getUuid());
    }

    private static void markStoreDirty(ServerWorld world) {
        GroxMemoryStore store = world.getPersistentStateManager().getOrCreate(
                GroxMemoryStore::fromNbt, GroxMemoryStore::new, "grox_villager_memories");
        store.markDirty();
    }

    private static void think(ServerWorld world, VillagerEntity villager) {
        GroxMemory m = memory(world, villager);
        List<HostileEntity> threats = world.getEntitiesByClass(HostileEntity.class,
                villager.getBoundingBox().expand(10), e -> e.isAlive());
        if (!threats.isEmpty()) {
            Entity threat = threats.get(0);
            m.lastThreat = world.getTime();
            m.fear = Math.min(100, m.fear + 12);
            m.mood = Math.max(0, m.mood - 3);
            m.remember("Почувствовал опасность рядом.");
            if (m.bravery < 65 || m.fear > 55) {
                double dx = villager.getX() - threat.getX();
                double dz = villager.getZ() - threat.getZ();
                double length = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                villager.getNavigation().startMovingTo(
                        villager.getX() + dx / length * 7,
                        villager.getY(),
                        villager.getZ() + dz / length * 7, 1.15);
            }
        } else {
            if (m.fear > 0) m.fear = Math.max(0, m.fear - 2);
            if (m.mood < 75) m.mood++;
        }
        markStoreDirty(world);
    }
}
