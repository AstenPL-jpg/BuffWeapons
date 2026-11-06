package pl.buffweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<dark_gray>[<gold>Bronie<dark_gray>] <gray>";

    private Text() {
    }

    public static Component c(String mini) {
        return MM.deserialize(mini).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component msg(String mini) {
        return c(PREFIX + mini);
    }

    public static String plain(String mini) {
        return MM.stripTags(mini);
    }

    public static List<Component> lore(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String l : lines) {
            out.add(c(l));
        }
        return out;
    }

    /** 7.0 -> "7", 7.5 -> "7.5", 1.25 -> "1.25" */
    public static String num(double d) {
        String s = String.format(Locale.ROOT, "%.2f", d);
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    public static String bar(double fraction, int length) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * length);
        return "<green>" + "█".repeat(filled) + "<dark_gray>" + "█".repeat(length - filled);
    }
}
