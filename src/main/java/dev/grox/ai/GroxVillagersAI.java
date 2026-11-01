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
        // Left-click: talk to the villager instead of hurting it.
        // Sneak + left-click: show personality stats (works in Survival too).
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || !(entity instanceof VillagerEntity villager)) return ActionResult.PASS;
            ServerWorld serverWorld = (ServerWorld) world;
            GroxMemory m = memory(serverWorld, villager);
            if (player.isSneaking()) {
                sendStats(player, m);
            } else {
                m.interactions++;
                m.remember("Игрок подошёл и заговорил.");
                markStoreDirty(serverWorld);
                String answer = GroxDialogue.greeting(m, villager);
                player.sendMessage(Text.literal("§6[Житель] §f" + answer), false);
            }
            return ActionResult.SUCCESS;
        });

        // Normal right-click is deliberately passed to Minecraft so trading,
        // profession screens and other vanilla villager interactions still work.
        // Sneak + right-click remains a quick personality panel.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(entity instanceof VillagerEntity villager)) return ActionResult.PASS;
            if (!player.isSneaking()) return ActionResult.PASS;
            GroxMemory m = memory((ServerWorld) world, villager);
            sendStats(player, m);
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
                    m.trust = Math.min(100, m.trust + 1);
                    m.mood = Math.min(100, m.mood + 1);
                    markStoreDirty(world);
                    String answer = GroxDialogue.answer(message, m, villager);
                    context.getSource().sendFeedback(() -> Text.literal("§6[" + villager.getName().getString() + "] §f" + answer), false);
                    return 1;
                }))
            )
        );

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (++tickCounter % 40 != 0) return;
            for (ServerPlayerEntity player : world.getPlayers()) {
                Box area = player.getBoundingBox().expand(48);
                for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class, area, v -> v.isAlive())) {
                    think(world, villager);
                }
            }
        });
    }

    private static void sendStats(net.minecraft.server.network.ServerPlayerEntity player, GroxMemory memory) {
        player.sendMessage(Text.literal("§6[Грокс] §fХарактер: смелость " + memory.bravery
                + ", доверие " + memory.trust + ", страх " + memory.fear
                + ", настроение " + memory.mood + ", разговоров " + memory.interactions), false);
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