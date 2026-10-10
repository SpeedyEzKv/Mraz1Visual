package dev.mraz1visual.ui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.ui.Anim;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Theme;
import dev.mraz1visual.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.IoSupplier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ресурспаки внутри настроек: «Включены» (верхний главнее) и «Все паки».
 * Изменения применяются при выходе из раздела — перезагрузка ресурсов одна, а не на каждый клик.
 */
final class PacksPanel {
    private static final int ROW_H = 30;
    private static final String[] TABS = {"ВКЛЮЧЕНЫ", "ВСЕ ПАКИ"};

    private final Minecraft mc = Minecraft.getInstance();
    private PackRepository repo;
    /** Выбранные id снизу вверх (как хранит игра). */
    private final List<String> selected = new ArrayList<>();
    private List<String> original = List.of();
    private final Map<String, Identifier> icons = new HashMap<>();
    private final Map<String, Float> hover = new HashMap<>();
    private int tab;
    private final float[] tabHover = new float[2];
    private float scroll, scrollTarget;
    private float addHover, folderHover;
    private int addX, folderX, btnY, addW, folderW;
    private String status = "";

    void open() {
        repo = mc.getResourcePackRepository();
        repo.reload();
        selected.clear();
        for (Pack p : repo.getSelectedPacks()) selected.add(p.getId());
        original = List.copyOf(selected);
        scroll = scrollTarget = 0;
    }

    /** Применить изменения (вызывается при уходе из раздела). */
    void apply() {
        if (repo == null || selected.equals(original)) return;
        repo.setSelected(selected);
        mc.options.updateResourcePacks(repo);
        original = List.copyOf(selected);
    }

    private List<Pack> rows() {
        List<Pack> out = new ArrayList<>();
        if (repo == null) return out;
        if (tab == 0) {
            for (int i = selected.size() - 1; i >= 0; i--) {
                Pack p = repo.getPack(selected.get(i));
                if (p != null) out.add(p);
            }
        } else {
            out.addAll(repo.getAvailablePacks());
        }
        return out;
    }

    private Identifier icon(Pack p) {
        if (icons.containsKey(p.getId())) return icons.get(p.getId());
        Identifier id = null;
        try (PackResources res = p.open()) {
            IoSupplier<InputStream> sup = res.getRootResource("pack.png");
            if (sup != null) {
                try (InputStream in = sup.get()) {
                    NativeImage img = NativeImage.read(in);
                    id = Identifier.fromNamespaceAndPath("mraz1visual", "pack_icon/" + Integer.toHexString(p.getId().hashCode()));
                    mc.getTextureManager().register(id, new DynamicTexture(() -> "mraz1visual pack icon", img));
                }
            }
        } catch (Exception ignored) {
            id = null;
        }
        icons.put(p.getId(), id);
        return id;
    }

    void render(Draw d, double mx, double my, float dt, int px, int py, int pw, int ph, float fade) {
        // вкладки
        int tx = px, ty = py - 22;
        for (int i = 0; i < 2; i++) {
            int w = Fonts.width(TABS[i], Fonts.Kind.HEAD);
            boolean hov = Widget.hit(mx, my, tx - 4, ty - 6, w + 8, 20);
            tabHover[i] = Anim.to(tabHover[i], hov ? 1 : 0, dt);
            d.text(TABS[i], Fonts.Kind.HEAD, tx, ty, i == tab ? Theme.TEXT : Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, tabHover[i]));
            if (i == tab) d.round(tx, py - 9, w, 2, 1, Theme.accent());
            tx += w + 18;
        }
        d.rect(px, py - 8, pw, 1, Theme.DIVIDER);
        d.text(tab == 0 ? "ВКЛЮЧЕНЫ — ВЕРХНИЙ ГЛАВНЕЕ" : "ВСЕ ПАКИ — КЛИК ВКЛЮЧАЕТ И ВЫКЛЮЧАЕТ", Fonts.Kind.HEAD, px, py + 2, Theme.TEXT_MUTED);

        int ly = py + 16, lh = ph - 40;
        List<Pack> rows = rows();
        scroll = Anim.to(scroll, scrollTarget, dt);
        d.clip(px - 4, ly, pw + 8, lh);
        int sc = Math.round(scroll);
        for (int i = 0; i < rows.size(); i++) {
            int ry = ly + i * ROW_H - sc;
            if (ry + ROW_H < ly) continue;
            if (ry > ly + lh) break;
            Pack p = rows.get(i);
            boolean on = selected.contains(p.getId());
            boolean hov = Widget.hit(mx, my, px - 4, ry, pw + 8, ROW_H) && my >= ly && my < ly + lh;
            float h = Anim.to(hover.getOrDefault(p.getId(), 0f), hov ? 1 : 0, dt);
            hover.put(p.getId(), h);
            if (h > 0.01f) d.round(px - 4, ry + 1, pw + 8, ROW_H - 2, Theme.RADIUS_SM, Colors.mulAlpha(0x0CFFFFFF, h));
            int is = ROW_H - 8;
            Identifier ic = icon(p);
            if (ic != null) d.g.blit(RenderPipelines.GUI_TEXTURED, ic, px, ry + 4, 0f, 0f, is, is, 64, 64, 64, 64, 0xFFFFFFFF);
            else d.round(px, ry + 4, is, is, 3, Theme.FIELD);
            int tx2 = px + is + 8;
            d.text(Draw.ellipsize(p.getTitle().getString(), pw - is - 80), Fonts.Kind.BOLD, tx2, ry + 4, Theme.TEXT);
            List<String> desc = descLines(p, pw - is - 80);
            for (int j = 0; j < desc.size(); j++) d.text(desc.get(j), tx2, ry + 13 + j * 8, Theme.TEXT_MUTED);
            // справа: состояние / порядок
            int rx = px + pw;
            if (tab == 1) {
                d.textRight(on ? "Включён" : p.isRequired() ? "Всегда" : "", rx, ry + 11, on ? Theme.accent() : Theme.TEXT_MUTED);
            } else if (hov && !p.isRequired() && !p.isFixedPosition()) {
                d.textRight("▲  ▼  ✕", rx, ry + 11, Theme.TEXT_DIM);
            }
        }
        if (rows.isEmpty()) d.text("Пусто", px, ly + 6, Theme.TEXT_MUTED);
        d.unclip();

        // кнопки
        btnY = py + ph - 14;
        folderW = Fonts.width("ОТКРЫТЬ ПАПКУ", Fonts.Kind.HEAD);
        addW = Fonts.width("ДОБАВИТЬ ПАКИ…", Fonts.Kind.HEAD);
        folderX = px + pw - folderW;
        addX = folderX - 22 - addW;
        folderHover = Anim.to(folderHover, Widget.hit(mx, my, folderX - 4, btnY - 4, folderW + 8, 16) ? 1 : 0, dt);
        addHover = Anim.to(addHover, Widget.hit(mx, my, addX - 4, btnY - 4, addW + 8, 16) ? 1 : 0, dt);
        d.text("ДОБАВИТЬ ПАКИ…", Fonts.Kind.HEAD, addX, btnY, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, addHover));
        d.text("ОТКРЫТЬ ПАПКУ", Fonts.Kind.HEAD, folderX, btnY, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, folderHover));
        if (!status.isEmpty()) d.text(status, px, btnY, Theme.TEXT_MUTED);
    }

    private final Map<String, List<String>> descCache = new HashMap<>();

    private List<String> descLines(Pack p, int w) {
        return descCache.computeIfAbsent(p.getId() + "@" + w, k -> {
            List<String> out = new ArrayList<>();
            String s = p.getDescription().getString().replace('\n', ' ');
            if (s.isEmpty()) return out;
            out.add(Draw.ellipsize(s, w));
            return out;
        });
    }

    boolean click(double mx, double my, int px, int py, int pw, int ph) {
        int tx = px, ty = py - 22;
        for (int i = 0; i < 2; i++) {
            int w = Fonts.width(TABS[i], Fonts.Kind.HEAD);
            if (Widget.hit(mx, my, tx - 4, ty - 6, w + 8, 20)) {
                tab = i;
                scroll = scrollTarget = 0;
                return true;
            }
            tx += w + 18;
        }
        if (Widget.hit(mx, my, folderX - 4, btnY - 4, folderW + 8, 16)) {
            dev.mraz1visual.overlay.Screenshots.openExternal(mc.getResourcePackDirectory().toAbsolutePath().toString());
            return true;
        }
        if (Widget.hit(mx, my, addX - 4, btnY - 4, addW + 8, 16)) {
            addPacks();
            return true;
        }
        int ly = py + 16, lh = ph - 40;
        if (!Widget.hit(mx, my, px - 4, ly, pw + 8, lh)) return false;
        List<Pack> rows = rows();
        int i = (int) Math.floor((my - ly + scroll) / ROW_H);
        if (i < 0 || i >= rows.size()) return true;
        Pack p = rows.get(i);
        if (p.isRequired()) return true;
        String id = p.getId();
        if (tab == 1) {
            if (selected.contains(id)) selected.remove(id);
            else selected.add(id);
            return true;
        }
        // «Включены»: справа ▲ ▼ ✕
        int rx = px + pw;
        int third = Fonts.width("▲  ▼  ✕") / 3;
        if (p.isFixedPosition() || mx < rx - third * 3) return true;
        int idx = selected.indexOf(id);
        if (mx >= rx - third) selected.remove(id);
        else if (mx >= rx - third * 2) { // вниз = ниже приоритет
            if (idx > 0 && !isFixed(selected.get(idx - 1))) java.util.Collections.swap(selected, idx, idx - 1);
        } else {
            if (idx < selected.size() - 1 && !isFixed(selected.get(idx + 1))) java.util.Collections.swap(selected, idx, idx + 1);
        }
        return true;
    }

    private boolean isFixed(String id) {
        Pack p = repo.getPack(id);
        return p == null || p.isFixedPosition();
    }

    void scroll(double amount, int ph) {
        int max = Math.max(0, rows().size() * ROW_H - (ph - 40));
        scrollTarget = (float) Math.max(0, Math.min(max, scrollTarget - amount * ROW_H));
    }

    /** Окно выбора файлов системы: копируем zip-архивы в папку ресурспаков. */
    private void addPacks() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.zip"));
            filters.flip();
            String res = TinyFileDialogs.tinyfd_openFileDialog("Ресурспаки", "", filters, "Ресурспак (.zip)", true);
            if (res == null) return;
            Path dir = mc.getResourcePackDirectory();
            int n = 0;
            for (String f : res.split("\\|")) {
                Path src = Path.of(f);
                if (!Files.isRegularFile(src)) continue;
                Files.copy(src, dir.resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                n++;
            }
            repo.reload();
            status = n > 0 ? "Добавлено: " + n + " — включи во «Все паки»" : "";
            tab = 1;
        } catch (Exception e) {
            status = "Не удалось добавить";
            ModuleManager.LOG.error("Mraz1Visual packs: add failed", e);
        }
    }
}
