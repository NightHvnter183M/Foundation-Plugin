package main;

import mindustry.gen.Player;
import java.util.Locale;
import java.util.ResourceBundle;

public class Localisation {
    private static final String BUNDLE_NAME = "localis";

    public static String local(Player player, String key, Object... args) {
        String lang = (player != null && player.locale != null && player.locale.length() >= 2)
                ? player.locale.substring(0, 2)
                : "en";

        if (!lang.equals("ru") && !lang.equals("en")) {
            lang = "en";
        }

        try {
            Locale locale = new Locale(lang);
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);

            String rawMessage = bundle.getString(key);
            if (args.length > 0) {
                return String.format(rawMessage, args);
            }

            return rawMessage;

        } catch (Exception e) {
            return "[" + key + "]";
        }
    }
}
