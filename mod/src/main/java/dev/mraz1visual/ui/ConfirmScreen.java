package dev.mraz1visual.ui;

import dev.mraz1visual.render.HudStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Окно-вопрос в стиле клиента: заголовок, пояснение и две кнопки. Esc — как первая кнопка. */
public final class ConfirmScreen extends Screen {
    private final String text, yes, no;
    private final Runnable onYes, onNo;
    private long opened;

    public ConfirmScreen(String title, String text, String yes, String no, Runnable onYes, Runnable onNo) {
        super(Component.literal(title));
        this.text = text;
        this.yes = yes;
        this.no = no;
        this.onYes = onYes;
        this.onNo = onNo;
    }

    @Override
    protected void init() {
        opened = System.nanoTime();
        int bw = 120, y = height / 2 + 14;
        addRenderableWidget(Button.builder(Component.literal(yes), b -> onYes.run()).bounds(width / 2 - bw - 4, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal(no), b -> onNo.run()).bounds(width / 2 + 4, y, bw, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        float t = Math.min(1f, (System.nanoTime() - opened) / 2e8f);
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        int w = Math.max(260, Fonts.width(text, Fonts.Kind.TEXT) + 32), h = 74;
        float x = width / 2f - w / 2f, y = height / 2f - 44;
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, height / 2f);
        g.pose().scale(0.95f + 0.05f * e, 0.95f + 0.05f * e);
        g.pose().translate(-width / 2f, -height / 2f);
        HudStyle.panel(g, x, y, w, h, 14, e);
        Sdf.fill(g, x + 14, y + 13, 4, 12, 2, HudStyle.RED);
        Fonts.draw(g, title.getString(), Fonts.Kind.BOLD, x + 24, y + 14, Colors.mulAlpha(HudStyle.TEXT, e));
        Fonts.draw(g, text, Fonts.Kind.TEXT, x + 14, y + 32, Colors.mulAlpha(HudStyle.TEXT_DIM, e));
        g.pose().popMatrix();
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public void onClose() {
        onYes.run();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
