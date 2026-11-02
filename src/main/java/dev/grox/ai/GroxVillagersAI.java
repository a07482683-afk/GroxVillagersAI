package dev.grox.ai;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
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
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class GroxVillagersAI implements ModInitializer {
    private static int tickCounter = 0;

    @Override
    public void onInitialize() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || !(entity instanceof VillagerEntity villager)
                    || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            if (serverPlayer.isSneaking()) {
                if (!serverPlayer.isCreative()) return ActionResult.PASS;
                sendStats(serverPlayer, memory((ServerWorld) world, villager));
                return ActionResult.SUCCESS;
            }

            ServerWorld serverWorld = (ServerWorld) world;
            GroxMemory m = memory(serverWorld, villager);
            m.interactions++;
            m.social = Math.min(100, m.social + 2);
            m.curiosity = Math.min(100, m.curiosity + 1);
            m.remember("Игрок подошёл и начал разговор.");
            m.trust = Math.min(100, m.trust + 1);
            markStoreDirty(serverWorld);
            serverPlayer.sendMessage(Text.literal("§6[" + villager.getName().getString()
                    + "] §f" + GroxDialogue.greeting(m, villager)), false);
            return ActionResult.SUCCESS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(entity instanceof VillagerEntity villager)
                    || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (!serverPlayer.isSneaking() || !serverPlayer.isCreative()) return ActionResult.PASS;
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
                    m.social = Math.min(100, m.social + 3);
                    m.curiosity = Math.min(100, m.curiosity + 2);
                    updateRelationship(message, m);
                    markStoreDirty(world);
                    String answer = GroxDialogue.answer(message, m, villager);
                    context.getSource().sendFeedback(() -> Text.literal("§6["
                            + villager.getName().getString() + "] §f" + answer), false);
                    return 1;
                }))
            )
        );

        // Listen to ordinary player chat. Villagers decide independently whether to react.
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String chat = message.getContent().getString().trim();
            if (!chat.isEmpty()) handleOrdinaryChat(sender, chat);
        });

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


    private static void handleOrdinaryChat(ServerPlayerEntity player, String rawMessage) {
        ServerWorld world = player.getServerWorld();
        String message = rawMessage.toLowerCase(Locale.ROOT);
        String action = null;

        if (containsAny(message, "смотрите на меня", "посмотрите на меня", "смотри на меня",
                "посмотри на меня", "все смотрите сюда", "все ко мне")) {
            action = "look";
        } else if (containsAny(message, "посмотри налево", "смотри налево", "повернись налево")) {
            action = "left";
        } else if (containsAny(message, "подними руку", "поднимите руку", "помаши", "помашите",
                "помахай")) {
            action = "hand";
        } else if (containsAny(message, "сядь", "садитесь", "присядь")) {
            action = "sit";
        }

        boolean addressedToVillagers = action != null || containsAny(message,
                "эй жители", "эй, жители", "жители", "привет", "здравствуйте", "как дела");
        if (!addressedToVillagers) return;

        List<VillagerEntity> nearby = world.getEntitiesByClass(VillagerEntity.class,
                player.getBoundingBox().expand(24), v -> v.isAlive());
        nearby.sort((a, b) -> Double.compare(a.squaredDistanceTo(player), b.squaredDistanceTo(player)));

        int replies = 0;
        for (VillagerEntity villager : nearby) {
            GroxMemory m = memory(world, villager);
            int chance = 22 + m.bravery / 5 + m.trust / 3 + m.social / 5
                    + m.curiosity / 8 - m.fear / 3;
            chance = Math.max(5, Math.min(95, chance));
            if (ThreadLocalRandom.current().nextInt(100) >= chance) continue;

            m.interactions++;
            m.social = Math.min(100, m.social + 1);
            m.curiosity = Math.min(100, m.curiosity + 1);

            String reply;
            if ("look".equals(action)) {
                villager.getLookControl().lookAt(player, 30.0F, 30.0F);
                reply = choose(m,
                        "Хорошо, я смотрю на тебя.",
                        "Я заметил тебя!",
                        "Слушаю внимательно.",
                        "Я повернулся. Что случилось?");
            } else if ("left".equals(action)) {
                villager.setYaw(villager.getYaw() - 45.0F);
                reply = choose(m, "Повернулся налево.", "Сейчас посмотрю туда.", "Хорошо, проверю эту сторону.");
            } else if ("hand".equals(action)) {
                reply = choose(m,
                        "Я услышал просьбу. Пока я не умею отдельно анимировать руки.",
                        "Я бы помахал, но анимация рук ещё не готова.",
                        "Понял тебя! Движения рук добавим отдельным этапом.");
            } else if ("sit".equals(action)) {
                reply = choose(m,
                        "Я услышал тебя. Отдельная анимация сидения ещё не подключена.",
                        "Пока я не умею садиться по команде, но понял просьбу.",
                        "Эту позу добавим, когда появится собственная система анимаций.");
            } else {
                reply = GroxDialogue.answer(rawMessage, m, villager);
            }

            if (m.fear > 70 && action != null) {
                reply = choose(m, "Я немного напуган и пока не решаюсь.", "Давай сначала убедимся, что всё спокойно.");
            } else if (ThreadLocalRandom.current().nextInt(100) > Math.min(95, 35 + m.bravery / 2 + m.trust / 4)) {
                reply = choose(m, "Я услышал, но пока останусь здесь.", "Не сейчас, пожалуйста.", "Я пока понаблюдаю со стороны.");
            }

            player.sendMessage(Text.literal("§6[" + villager.getName().getString() + "] §f" + reply), false);
            if (action != null) m.remember("Игрок попросил: " + action);
            markStoreDirty(world);
            if (++replies >= 4) break; // Keep chat readable in large villages.
        }
    }

    private static boolean containsAny(String text, String... fragments) {
        for (String fragment : fragments) if (text.contains(fragment)) return true;
        return false;
    }

    private static String choose(GroxMemory memory, String... lines) {
        return lines[ThreadLocalRandom.current().nextInt(lines.length)];
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
                + " | Настроение: " + memory.mood + " | Общительность: " + memory.social
                + " | Любопытство: " + memory.curiosity
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
            boolean newThreat = m.lastThreat == 0 || world.getTime() - m.lastThreat > 200;
            m.lastThreat = world.getTime();
            m.fear = Math.min(100, m.fear + 12);
            if (newThreat) m.setExpression("surprised", 3);
            m.mood = Math.max(0, m.mood - 3);
            m.remember("Почувствовал опасность рядом.");
            if (m.bravery < 65 || m.fear > 55) {
                double dx = villager.getX() - threat.getX();
                double dz = villager.getZ() - threat.getZ();
                double length = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                villager.getNavigation().startMovingTo(villager.getX() + dx / length * 7,
                        villager.getY(), villager.getZ() + dz / length * 7, 1.15);
            }
        } else {
            if (m.fear > 0) m.fear = Math.max(0, m.fear - 2);
            if (m.mood < 75) m.mood++;
        }

        // Update the saved emotional state; visual rendering is a separate upcoming stage.
        m.updateExpression();

        // Social stage: nearby villagers gradually become more sociable.
        if (world.getTime() % 200 == 0) {
            List<VillagerEntity> neighbors = world.getEntitiesByClass(VillagerEntity.class,
                    villager.getBoundingBox().expand(6), other -> other != villager && other.isAlive());
            if (!neighbors.isEmpty()) {
                m.social = Math.min(100, m.social + 2);
                m.mood = Math.min(100, m.mood + 1);
                m.trust = Math.min(100, m.trust + 1);
                if (world.random.nextInt(4) == 0) m.remember("Провёл время рядом с другими жителями.");
            } else {
                m.social = Math.max(0, m.social - 1);
            }
            if (villager.isSleeping()) {
                m.fear = Math.max(0, m.fear - 2);
                m.mood = Math.min(100, m.mood + 2);
            }
            markStoreDirty(world);
        }
    }
}
