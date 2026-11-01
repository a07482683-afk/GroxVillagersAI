package dev.grox.ai;

import net.minecraft.entity.passive.VillagerEntity;
import java.util.Locale;

public final class GroxDialogue {
    private GroxDialogue() {}

    public static String greeting(GroxMemory m, VillagerEntity villager) {
        String name = villager.getName().getString();
        if (m.fear > 65) return "Не подходи слишком быстро... Мне сейчас тревожно.";
        if (m.trust > 75) return "О, это снова ты, " + name + "! Рад тебя видеть. Что нового?";
        if (m.mood < 25) return "Привет... Сегодня у меня не самый лучший день.";
        switch (Math.floorMod(m.interactions, 6)) {
            case 0: return "Привет! Как у тебя дела?";
            case 1: return "Здравствуй. Ты что-нибудь интересное видел?";
            case 2: return "Рад встрече. В деревне сегодня спокойно.";
            case 3: return "Привет! Мне нравится знакомиться с путешественниками.";
            case 4: return "Снова ты! О чём поговорим?";
            default: return "Здравствуй! Если хочешь поговорить, напиши /grox и своё сообщение.";
        }
    }

    public static String answer(String input, GroxMemory m, VillagerEntity villager) {
        String s = input.toLowerCase(Locale.ROOT);
        if (s.contains("привет") || s.contains("здравств")) return greeting(m, villager);
        if (s.contains("как дела") || s.contains("как ты")) return m.mood > 65 ? "У меня хорошее настроение сегодня." : "Бывает по-разному. Я пока присматриваюсь к тебе.";
        if (s.contains("страш") || s.contains("боишься")) return m.fear > 35 ? "Да, сейчас мне тревожно." : "Я стараюсь сохранять спокойствие.";
        if (s.contains("друг") || s.contains("довер")) return m.trust > 60 ? "Я уже начинаю тебе доверять." : "Доверие нужно заслужить.";
        if (s.contains("оруж") || s.contains("меч")) return m.bravery > 60 ? "Я не люблю угрозы, но постоять за себя могу." : "Пожалуйста, не размахивай этим рядом со мной.";
        if (s.contains("помощ") || s.contains("работ")) return "Скажи точнее, что нужно сделать. Я подумаю.";
        if (s.contains("пока") || s.contains("до свид")) return "До встречи. Я запомню наш разговор.";
        if (s.contains("торг") || s.contains("обмен")) return "Для торговли нажми по мне ПКМ. Я покажу свои обычные предложения.";
        if (s.contains("дом") || s.contains("деревн")) return "Это наш дом. Я хочу, чтобы здесь было безопасно.";
        if (s.contains("имя") || s.contains("зовут")) return "Меня зовут " + villager.getName().getString() + ". А как зовут тебя?";
        if (s.contains("погода") || s.contains("дожд")) return "Я заметил погоду. Надеюсь, скоро станет яснее.";
        return "Интересно... Ты сказал: «" + input + "». Я запомню это и подумаю.";
    }
}