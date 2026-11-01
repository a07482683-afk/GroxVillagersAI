package dev.grox.ai;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.PersistentState;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class GroxVillagersAI implements ModInitializer {
    private static int tickCounter = 0;

    @Override
    public void onInitialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(entity instanceof VillagerEntity villager)) return ActionResult.PASS;
            GroxMemory memory = memory((ServerWorld) world, villager);
            if (player.isSneaking()) {
                player.sendMessage(Text.literal("§6[Грокс] §fХарактер: смелость " + memory.bravery
                        + ", доверие " + memory.trust + ", страх " + memory.fear
                        + ", настроение " + memory.mood), false);
            } else {
                player.sendMessage(Text.literal("§6[Житель] §f" + GroxDialogue.answer("привет", memory, villager)), false);
            }
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
                    m.trust = Math.min(100, m.trust + 1);
                    m.mood = Math.min(100, m.mood + 1);
                    m.markDirty();
                    String answer = GroxDialogue.answer(message, m, villager);
                    context.getSource().sendFeedback(() -> Text.literal("§6[Грокс] §f" + answer), false);
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

    private static GroxMemory memory(ServerWorld world, VillagerEntity villager) {
        GroxMemoryStore store = world.getPersistentStateManager().getOrCreate(
                GroxMemoryStore::fromNbt, GroxMemoryStore::new, "grox_villager_memories");
        return store.get(villager.getUuid());
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
        m.markDirty();
    }
}
