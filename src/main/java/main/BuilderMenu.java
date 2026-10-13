package main;

import arc.math.Rand;
import arc.struct.ObjectMap;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.ui.Menus;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder;

public class BuilderMenu {

    public interface ClickHandler {
        void clicked(Player player, String action, MenuResult result);
    }

    private static final Rand rand = new Rand();

    public final int id;
    private final boolean hideOnClick, fillScreen;
    private final ObjectMap<String, Long> tokens = new ObjectMap<>();

    public BuilderMenu(boolean hideOnClick, boolean fillScreen, ClickHandler handler) {
        this.hideOnClick = hideOnClick;
        this.fillScreen = fillScreen;
        id = Menus.registerMenuBuilder((player, result) -> {
            Long token = tokens.get(player.uuid());
            if (token == null || token != result.token) return; // not shown to this player, or an older window

            if (result.wasCancelled()) {
                tokens.remove(player.uuid()); // closed
                return;
            }
            if (hideOnClick) tokens.remove(player.uuid());
            handler.clicked(player, result.result, result);
        });
    }

    public void show(Player player, String title, UiBuilder.NodeBuilder<?> ui) {
        long token = rand.nextLong();
        tokens.put(player.uuid(), token);
        Call.menuBuilder(player.con, id, token, title, hideOnClick, true, fillScreen, ui);
    }

    public boolean isOpen(Player player) {
        return tokens.containsKey(player.uuid());
    }

    public void update(Player player, String elementId, UiBuilder.NodeBuilder<?> ui) {
        if (isOpen(player)) Call.menuBuilderUpdate(player.con, id, elementId, ui);
    }

    public void hide(Player player) {
        if (tokens.remove(player.uuid()) != null) Call.hideMenuBuilder(player.con, id);
    }

    public void hideAll() {
        Groups.player.each(this::hide);
        tokens.clear();
    }

    public void forget(Player player) {
        tokens.remove(player.uuid());
    }
}
