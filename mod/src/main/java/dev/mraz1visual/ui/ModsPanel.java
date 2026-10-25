package dev.mraz1visual.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Вкладка «Моды»: загруженные Fabric-моды (без встроенных библиотек и самой игры) и кнопка «Открыть папку модов».
 * Список собирается один раз при открытии вкладки — в кадре только рисуется.
 */
public final class ModsPanel implements Panel {
    private static final int ROW = 30, HEAD = 40;

    private record Entry(String name, String version, String id, String description) {}

    private final List<Entry> mods = new ArrayList<>();
    private String count = "";
    private float scroll, target;
    private int maxScroll, bx, by, bw, bh;

    @Override
    public void opened() {
        mods.clear();
        for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
            ModMetadata m = c.getMetadata();
            String id = m.getId();
            // сама игра, Java и вложенные библиотеки — не моды, которые ставил игрок
            if (id.equals("minecraft") || id.equals("java") || c.getContainingMod().isPresent()) continue;
            // модули Fabric API (в dev-клиенте лежат отдельно) — это один мод «Fabric API»
            if (id.startsWith("fabric-") && !id.equals("fabric-api")) continue;
            if (m.getType().equals("builtin")) continue;
            mods.add(new Entry(m.getName(), m.getVersion().getFriendlyString(), id, m.getDescription().replace('\n', ' ')));
        }
        mods.sort((a, b) -> a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT)));
        count = Lang.get("mraz1visual.mods.count", mods.size());
        scroll = target = 0;
    }

    @Override
    public void render(Draw d, double mx, double my, float dt, int x, int y, int w, int h) {
        d.round(x, y, w, h, 14, Theme.isLight() ? 0x60FFFFFF : 0x40101018);
        d.text(Lang.get("mraz1visual.mods.title"), Fonts.Kind.BOLD, x + 14, y + 12, Theme.TEXT);
        d.text(count, x + 14, y + 25, Theme.TEXT_MUTED);
        String open = Lang.get("mraz1visual.mods.open_folder");
        bw = d.width(open) + 24;
        bh = 20;
        bx = x + w - bw - 12;
        by = y + 12;
        boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        d.round(bx, by, bw, bh, 10, Colors.withAlpha(Theme.accent(), hov ? 0xC0 : 0x80));
        d.textCenter(open, bx + bw / 2, Draw.textY(by, bh), 0xFFFFFFFF);

        int ly = y + HEAD, lh = h - HEAD - 6;
        maxScroll = Math.max(0, mods.size() * ROW - lh);
        target = Math.max(0, Math.min(maxScroll, target));
        scroll += (target - scroll) * Math.min(1f, dt * 14f);
        if (mods.isEmpty()) {
            d.textCenter(Lang.get("mraz1visual.mods.empty"), x + w / 2, ly + lh / 2, Theme.TEXT_MUTED);
            return;
        }
        d.clip(x + 4, ly, w - 8, lh);
        int s = Math.round(scroll);
        for (int i = 0; i < mods.size(); i++) {
            int ry = ly + i * ROW - s;
            if (ry + ROW < ly || ry > ly + lh) continue;
            Entry e = mods.get(i);
            if (mx >= x + 8 && mx < x + w - 8 && my >= ry && my < ry + ROW - 2 && my >= ly && my < ly + lh) d.round(x + 8, ry, w - 16, ROW - 2, 8, Theme.HOVER);
            int vw = d.width(e.version);
            d.text(Draw.ellipsize(e.name, w / 3), Fonts.Kind.BOLD, x + 16, ry + 5, Theme.TEXT);
            d.text(Draw.ellipsize(e.description.isEmpty() ? e.id : e.description, w - 48 - vw), x + 16, ry + 16, Theme.TEXT_MUTED);
            d.textRight(e.version, x + w - 16, ry + 5, Theme.TEXT_DIM);
        }
        d.unclip();
    }

    @Override
    public boolean click(double mx, double my, int button) {
        if (button == 0 && mx >= bx && mx < bx + bw && my >= by && my < by + bh) {
            java.nio.file.Path dir = FabricLoader.getInstance().getGameDir().resolve("mods");
            try {
                java.nio.file.Files.createDirectories(dir);
            } catch (java.io.IOException ignored) {
            }
            dev.mraz1visual.compat.Platform.openPath(dir);
            return true;
        }
        return false;
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        target -= (float) amount * ROW * 2;
        return true;
    }
}
