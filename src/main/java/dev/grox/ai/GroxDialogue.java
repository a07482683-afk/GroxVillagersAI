package dev.grox.ai;

import net.minecraft.entity.passive.VillagerEntity;
import java.util.Locale;

public final class GroxDialogue {
    private GroxDialogue() {}

    public static String answer(String input, GroxMemory m, VillagerEntity villager) {
        String s = input.toLowerCase(Locale.ROOT);
        if (s.contains("привет") || s.contains("здравств")) return "Здравствуй. Я " + villager.getName().getString() + ". Рад встрече.";
        if (s.contains("как дела") || s.contains("как ты")) return m.mood > 65 ? "У меня хорошее настроение сегодня." : "Бывает по-разному. Я пока присматриваюсь к тебе.";
        if (s.contains("страш") || s.contains("боишься")) return m.fear > 35 ? "Да, сейчас мне тревожно." : "Я стараюсь сохранять спокойствие.";
        if (s.contains("друг") || s.contains("довер")) return m.trust > 60 ? "Я уже начинаю тебе доверять." : "Доверие нужно заслужить.";
        if (s.contains("оруж") || s.contains("меч")) return m.bravery > 60 ? "Я не люблю угрозы, но постоять за себя могу." : "Пожалуйста, не размахивай этим рядом со мной.";
        if (s.contains("помощ") || s.contains("работ")) return "Скажи точнее, что нужно сделать. Я подумаю.";
        if (s.contains("пока") || s.contains("до свид")) return "До встречи. Я запомню наш разговор.";
        return "Я услышал тебя: «" + input + "». Мне нужно время, чтобы это обдумать.";
    }
}
