package main;

import mindustry.gen.Player;
import arc.struct.ObjectMap;
import arc.util.Log;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class Localisation {
    private static final ObjectMap<String, Properties> langCache = new ObjectMap<>();
    private static final String BASE_NAME = "localis";

    public static String local(Player player, String key, Object... args) {
        String lang = (player != null && player.locale != null && player.locale.length() >= 2)
                ? player.locale.substring(0, 2)
                : "en";

        if (!lang.equals("ru") && !lang.equals("en")) {
            lang = "en";
        }

        if (!langCache.containsKey(lang)) {
            loadLanguage(lang);
        }

        Properties props = langCache.get(lang);

        if ((props == null || !props.containsKey(key)) && !lang.equals("en")) {
            if (!langCache.containsKey("en")) loadLanguage("en");
            props = langCache.get("en");
        }

        if (props == null || !props.containsKey(key)) {
            return "[" + key + "]";
        }

        String rawMessage = props.getProperty(key);

        if (args.length > 0) {
            return String.format(rawMessage, args);
        }

        return rawMessage;
    }

    private static void loadLanguage(String lang) {
        Properties props = new Properties();

        String fileName = "/" + BASE_NAME + "_" + lang + ".properties";
        InputStream in = Localisation.class.getResourceAsStream(fileName);

        if (in == null && lang.equals("en")) {
            fileName = "/" + BASE_NAME + ".properties";
            in = Localisation.class.getResourceAsStream(fileName);
        }

        if (in != null) {
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                props.load(reader);
                Log.info("Localisation successfully loaded: " + fileName);
            } catch (Exception e) {
                Log.err("Error loading localisation: " + fileName, e);
            }
        } else {
            Log.warn("Localisation not fount: " + fileName);
        }

        langCache.put(lang, props);
    }
}