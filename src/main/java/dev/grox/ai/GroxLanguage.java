package dev.grox.ai;

import java.util.concurrent.ThreadLocalRandom;

public final class GroxLanguage {
    private GroxLanguage() {}

    public static boolean supported(String code) {
        return switch (code.toLowerCase()) {
            case "ru", "tg", "en", "es", "fr", "de", "pt", "ar", "zh", "hi", "ja" -> true;
            default -> false;
        };
    }

    public static String languageList() {
        return "ru, tg, en, es, fr, de, pt, ar, zh, hi, ja";
    }

    public static String passingComment(String code) {
        String[][] lines = {
            {"О, кто-то проходит мимо.", "Кажется, у нас гость.", "Интересно, куда он направляется."},
            {"Оҳ, касе аз наздамон гузашт.", "Ба назар меҳмон омад.", "Ҷолиб, ӯ ба куҷо меравад?"},
            {"Oh, someone is passing by.", "Looks like we have a visitor.", "I wonder where they are going."},
            {"Oh, alguien está pasando.", "Parece que tenemos visita.", "Me pregunto adónde irá."},
            {"Oh, quelqu'un passe par ici.", "On dirait que nous avons un visiteur.", "Je me demande où il va."},
            {"Oh, da geht jemand vorbei.", "Wir haben wohl Besuch.", "Wohin die Person wohl geht?"},
            {"Olha, alguém está passando.", "Parece que temos visita.", "Para onde será que vai?"},
            {"أوه، هناك شخص يمر.", "يبدو أن لدينا زائراً.", "إلى أين يذهب يا ترى؟"},
            {"哦，有人经过。", "看来有客人来了。", "不知道他要去哪里。"},
            {"अरे, कोई यहाँ से गुजर रहा है।", "लगता है हमारे यहाँ मेहमान आए हैं।", "वे कहाँ जा रहे होंगे?"},
            {"あ、誰かが通りかかった。", "お客さんが来たみたい。", "どこへ行くのかな。"}
        };
        return pick(code, lines);
    }

    public static String idleComment(String code) {
        String[][] lines = {
            {"Сегодня в деревне спокойно.", "Интересно, что нового у соседей?", "Пожалуй, немного прогуляюсь."},
            {"Имрӯз деҳа ором аст.", "Ҳамсояҳо чӣ навигарӣ доранд?", "Шояд каме сайр кунам."},
            {"The village is peaceful today.", "I wonder what the neighbors are doing.", "Maybe I'll take a little walk."},
            {"Hoy el pueblo está tranquilo.", "¿Qué novedades tendrán los vecinos?", "Quizá dé un pequeño paseo."},
            {"Le village est calme aujourd'hui.", "Quelles nouvelles chez les voisins ?", "Je vais peut-être me promener."},
            {"Heute ist es ruhig im Dorf.", "Was machen wohl die Nachbarn?", "Vielleicht mache ich einen Spaziergang."},
            {"A vila está tranquila hoje.", "Que novidades terão os vizinhos?", "Talvez eu dê uma caminhada."},
            {"القرية هادئة اليوم.", "ماذا يفعل الجيران؟", "ربما أتمشى قليلاً."},
            {"今天村庄很平静。", "邻居们有什么新消息吗？", "也许我去散散步。"},
            {"आज गाँव में शांति है।", "पड़ोसी क्या कर रहे होंगे?", "शायद थोड़ी सैर करूँ।"},
            {"今日は村が静かだな。", "近所のみんなは何をしているかな？", "少し散歩しようかな。"}
        };
        return pick(code, lines);
    }

    private static String pick(String code, String[][] table) {
        int index = switch (code.toLowerCase()) {
            case "tg" -> 1; case "en" -> 2; case "es" -> 3; case "fr" -> 4;
            case "de" -> 5; case "pt" -> 6; case "ar" -> 7; case "zh" -> 8;
            case "hi" -> 9; case "ja" -> 10; default -> 0;
        };
        String[] options = table[index];
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }
}
