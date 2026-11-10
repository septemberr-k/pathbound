package dev.pathbound;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Full-screen difficulty picker: one card per selectable difficulty in rows that wrap and scroll, an optional
 * confirmation, no way for the host to close it without choosing. Look and motion: {@link ChooserLayout}.
 * Animations are time-based ({@link Anim}): the screen fades in, cards slide up one after another, a hovered card
 * rises and glows, a clicked card dips before the confirmation, the selected card pulses.
 */
public final class ChooserScreen extends Screen {
    private static final int LINE = 10, ICON = 16;

    private final boolean canChoose;
    private final ChooserLayout layout = ChooserLayout.get();
    private final ChooserLayout.Animations anim = layout.anim();
    private final List<Card> cards = new ArrayList<>();
    private List<FormattedCharSequence> subtitle = List.of(), hint = List.of();
    private int headerY, gridY, gridH, cardW, cardH, nameRow, scroll, maxScroll;
    /** Pixel sizes of icon and banner textures (1 texel = 1 GUI pixel); a missing texture maps to null. */
    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();
    private String hovered, selected, forcedHover;
    /** Kept across init() (resize, coming back from the confirmation) so the intro plays once. */
    private final long openedAt = Util.getMillis();
    private long lastFrame = openedAt, pressedAt;
    private Difficulty pressed;

    /** A card's place on screen (without the scroll offset), its wrapped description and its current lift. */
    private static final class Card {
        final Difficulty d;
        final int index, x, y;
        final List<FormattedCharSequence> desc;
        double lift;

        Card(Difficulty d, int index, int x, int y, List<FormattedCharSequence> desc) {
            this.d = d;
            this.index = index;
            this.x = x;
            this.y = y;
            this.desc = desc;
        }
    }

    ChooserScreen(boolean canChoose) {
        super(Component.translatable("pathbound.title"));
        this.canChoose = canChoose;
    }

    private String lang() {
        return minecraft.getLanguageManager().getSelected();
    }

    /** Sizes the cards to their text and wraps them into as many columns as fit (or the layout's column count). */
    @Override
    protected void init() {
        List<Difficulty> list = Difficulties.client().stream().filter(Difficulty::selectable).toList();
        int pad = layout.padding(), gap = width < 320 ? Math.min(6, layout.spacing()) : layout.spacing();
        int n = Math.max(1, list.size());
        int fit = Math.max(1, (width - 16 + gap) / (Math.min(layout.cardWidth(), Math.max(90, width - 16)) + gap));
        int cols = Math.min(n, layout.columns() > 0 ? Math.min(layout.columns(), fit) : fit);
        cardW = Math.max(90, Math.min(layout.cardWidth(), (width - 16 - (cols - 1) * gap) / cols));
        int rows = (list.size() + cols - 1) / cols;
        subtitle = font.split(layout.subtitle().resolve(lang()), Math.max(100, width - 24));
        hint = font.split(layout.hint().resolve(lang()), cardW - pad);
        int lines = 0;
        List<List<FormattedCharSequence>> descs = new ArrayList<>();
        for (Difficulty d : list) {
            List<FormattedCharSequence> desc = font.split(d.description().resolve(lang()), cardW - 2 * pad);
            descs.add(desc);
            lines = Math.max(lines, desc.size());
        }
        nameRow = LINE;
        for (Difficulty d : list) nameRow = Math.max(nameRow, headerHeight(d));
        cardH = Math.max(layout.cardMinHeight(), pad + nameRow + pad + lines * LINE + pad + hint.size() * LINE + pad);
        int header = LINE + 4 + subtitle.size() * LINE + 8;
        int total = rows * cardH + Math.max(0, rows - 1) * gap;
        // room above the grid for the hover lift, so a raised top card is not cut off
        int lift = anim.enabled() ? anim.hoverLift() + 2 : 0;
        headerY = Math.max(4, (height - header - total) / 2);
        gridY = headerY + header + lift;
        gridH = Math.max(cardH, Math.min(total, height - gridY - 4));
        maxScroll = Math.max(0, total - gridH);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        cards.clear();
        for (int i = 0; i < list.size(); i++) {
            int row = i / cols, col = i % cols;
            int inRow = Math.min(cols, list.size() - row * cols);
            int rowW = inRow * cardW + (inRow - 1) * gap;
            cards.add(new Card(list.get(i), i, (width - rowW) / 2 + col * (cardW + gap), gridY + row * (cardH + gap), descs.get(i)));
        }
    }

    /** Whether this is the chooser itself rather than the waiting notice. */
    boolean canChoose() {
        return canChoose;
    }

    /** The host must choose; anyone else only waits and may close the notice and play on. */
    @Override
    public boolean shouldCloseOnEsc() {
        return !canChoose;
    }

    /** 0..1: how far the screen has faded in. */
    private double fadeIn(long now) {
        return anim.enabled() ? Anim.Easing.EASE_OUT.apply(Anim.progress(now, openedAt, 0, anim.fadeMs())) : 1;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) renderPanorama(g, partialTick);
        if (layout.blur()) renderBlurredBackground(partialTick);
        float a = (float) fadeIn(Util.getMillis());
        if (layout.bgTexture() != null) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, a);
            if (layout.bgTile()) g.blit(layout.bgTexture(), 0, 0, 0, 0, width, height, 32, 32);
            else g.blit(layout.bgTexture(), 0, 0, 0, 0, width, height, width, height);
            g.flush();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else if (!layout.blur() && layout.bgColor() == 0) {
            renderMenuBackground(g);
        }
        if (layout.bgColor() != 0) g.fill(0, 0, width, height, Anim.withAlpha(layout.bgColor(), a));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        double dt = Math.min(100, now - lastFrame);
        lastFrame = now;
        super.render(g, mouseX, mouseY, partialTick);
        double fade = fadeIn(now);
        int titleY = headerY - (int) Math.round((1 - fade) * 6);
        g.drawCenteredString(font, layout.title().resolve(lang()), width / 2, titleY, Anim.withAlpha(layout.titleColor(), fade));
        if (!canChoose) {
            // On a dedicated server there is no host, only operators and the console: say how to get past this.
            boolean server = minecraft.getSingleplayerServer() == null && minecraft.getCurrentServer() != null && !minecraft.getCurrentServer().isLan();
            g.drawCenteredString(font, Component.translatable(server ? "pathbound.waiting.server" : "pathbound.waiting"), width / 2, height / 2, Anim.withAlpha(0xFFD0D0D0, fade));
            if (server) {
                int y = height / 2 + LINE + 4;
                for (FormattedCharSequence line : font.split(Component.translatable("pathbound.waiting.hint"), Math.max(100, width - 40))) {
                    g.drawCenteredString(font, line, width / 2, y, Anim.withAlpha(0xFF9A9AA6, fade));
                    y += LINE;
                }
            }
            return;
        }
        int y = titleY + LINE + 4;
        for (FormattedCharSequence line : subtitle) {
            g.drawCenteredString(font, line, width / 2, y, Anim.withAlpha(layout.subtitleColor(), fade));
            y += LINE;
        }
        Card over = cardAt(mouseX, mouseY);
        if (forcedHover != null) for (Card c : cards) if (c.d.id().equals(forcedHover)) over = c;
        String now2 = over == null ? null : over.d.id();
        if (now2 != null && !now2.equals(hovered) && layout.hoverSound() != null) play(layout.hoverSound());
        hovered = now2;
        // A clicked card dips first; the confirmation (or the request) follows when the dip is over.
        if (pressed != null && now - pressedAt >= (anim.enabled() ? anim.pressMs() : 0)) {
            Difficulty d = pressed;
            pressed = null;
            choose(d);
            if (minecraft.screen != this) return;
        }
        g.enableScissor(0, gridY - anim.hoverLift() - 4, width, gridY + gridH + 4);
        for (Card c : cards) {
            boolean isHovered = c == over && pressed == null;
            c.lift = anim.enabled() ? Anim.approach(c.lift, isHovered || c.d.id().equals(selected) ? anim.hoverLift() : 0, dt, anim.hoverMs()) : 0;
            drawCard(g, c, isHovered, now);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(12, gridH * gridH / (gridH + maxScroll));
            int barY = gridY + (gridH - barH) * scroll / maxScroll;
            g.fill(width - 6, gridY, width - 3, gridY + gridH, 0x40FFFFFF);
            g.fill(width - 6, barY, width - 3, barY + barH, 0xC0FFFFFF);
        }
    }

    private void drawCard(GuiGraphics g, Card c, boolean isHovered, long now) {
        Difficulty d = c.d;
        int x = c.x, top = c.y - scroll, pad = layout.padding();
        boolean isSelected = d.id().equals(selected), lit = isHovered || isSelected;
        int color = d.color() | 0xFF000000;
        // intro: each card in turn slides up and grows from 92 %; its text fades in. Sprites and item icons ignore
        // the shader alpha, so the card itself does not fade: it simply is not drawn before its turn.
        double alpha = 1, slide = 0, scale = 1;
        if (anim.enabled()) {
            long delay = anim.fadeMs() / 2 + (long) c.index * anim.staggerMs();
            alpha = Anim.progress(now, openedAt, delay, anim.introMs());
            if (alpha <= 0) return;
            double eased = anim.introEasing().apply(alpha);
            slide = (1 - eased) * anim.introOffset();
            scale = 0.92 + 0.08 * Math.min(1, eased);
            if (pressed == d) scale *= 1 - (1 - anim.pressScale()) * Anim.dip(now, pressedAt, anim.pressMs());
        }
        double raise = c.lift;
        float cx = x + cardW / 2f, cy = top + cardH / 2f;
        // shadow on the "ground" grows as the card rises
        // shadow and glow hug the visible frame: frame_inset skips the sprite's transparent margin (spikes, fog)
        int in = frameSprite(d, "") != null ? layout.frameInset() : 0;
        if (raise > 0.3) {
            int s = (int) Math.round(raise);
            g.fill(x + in + 2, top + cardH - in - 1, x + cardW - in - 2, top + cardH - in + s, Anim.withAlpha(0x60000000, alpha * raise / Math.max(1, anim.hoverLift())));
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy + slide - raise, 0);
        g.pose().scale((float) scale, (float) scale, 1f);
        g.pose().translate(-cx, -cy, 0);
        if (anim.enabled() && anim.glow() && (raise > 0.3 || isSelected)) {
            double strength = isSelected ? Anim.pulse(now, anim.selectedPulseMs(), 0.35, 0.8) : 0.45 * raise / Math.max(1, anim.hoverLift());
            g.fill(x + in - 2, top + in - 2, x + cardW - in + 2, top + cardH - in + 2, Anim.withAlpha(color & 0x00FFFFFF | 0xFF000000, strength * 0.5));
        }
        ResourceLocation sprite = frameSprite(d, isSelected ? "_selected" : isHovered ? "_hovered" : "");
        if (sprite != null) {
            if (layout.tintFrame()) RenderSystem.setShaderColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1f);
            g.blitSprite(sprite, x, top, cardW, cardH);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            g.fill(x, top, x + cardW, top + cardH, lit ? layout.cardHoverColor() : layout.cardColor());
            g.renderOutline(x, top, cardW, cardH, lit ? color : (color & 0x00FFFFFF) | 0x80000000);
        }
        int nameColor = Anim.withAlpha(layout.nameColor() != null ? layout.nameColor() : color, alpha);
        Component name = d.name().resolve(lang());
        // header row: icon, then the drawn banner (or the name), centred together and on the row's middle
        boolean icon = layout.icons() && d.icon() != null;
        int[] is = icon ? iconSize(d.icon()) : new int[] {0, 0};
        ResourceLocation banner = bannerTexture(d);
        int[] bs = banner != null ? size(banner) : new int[] {font.width(name), 8};
        int w = (icon ? is[0] + 4 : 0) + bs[0];
        int ix = x + (cardW - w) / 2, rowTop = top + pad;
        if (icon) {
            int iy = rowTop + (nameRow - is[1]) / 2;
            // the icon of a hovered card bobs gently
            double bob = anim.enabled() && isHovered ? -anim.iconBob() * Anim.pulse(now, 900, 0, 1) : 0;
            g.pose().pushPose();
            g.pose().translate(ix + is[0] / 2f, iy + is[1] / 2f + bob, 0);
            g.pose().scale((float) alpha, (float) alpha, 1f);   // the icon grows in with the text
            g.pose().translate(-(ix + is[0] / 2f), -(iy + is[1] / 2f), 0);
            drawIcon(g, d.icon(), ix, iy);
            g.pose().popPose();
            ix += is[0] + 4;
        }
        if (banner != null) {
            RenderSystem.enableBlend();
            g.blit(banner, ix, rowTop + (nameRow - bs[1]) / 2, 0, 0, bs[0], bs[1], bs[0], bs[1]);
        } else {
            g.drawString(font, name, ix, rowTop + (nameRow - 8) / 2, nameColor);
        }
        int y = rowTop + nameRow + pad;
        int hintY = top + cardH - pad - hint.size() * LINE + 1;
        // centred text also sits in the middle of the room between the header and the hint
        if (layout.centerText()) y += Math.max(0, (hintY - pad / 2 - y - c.desc.size() * LINE) / 2);
        for (FormattedCharSequence line : c.desc) {
            if (y + LINE > hintY) break;
            if (layout.centerText()) g.drawCenteredString(font, line, x + cardW / 2, y, Anim.withAlpha(layout.textColor(), alpha));
            else g.drawString(font, line, x + pad, y, Anim.withAlpha(layout.textColor(), alpha));
            y += LINE;
        }
        // Always say the card is clickable; under the cursor it takes the difficulty's colour and breathes softly.
        int hintColor = Anim.withAlpha(lit ? color : layout.hintColor(), alpha);
        if (lit && anim.enabled() && anim.hintPulseMs() > 0) hintColor = Anim.withAlpha(hintColor, alpha * Anim.pulse(now, anim.hintPulseMs(), 0.65, 1));
        for (FormattedCharSequence line : hint) {
            g.drawCenteredString(font, line, x + cardW / 2, hintY, hintColor);
            hintY += LINE;
        }
        g.pose().popPose();
    }

    /** The card sprite for a state: the difficulty's own frame, else the layout's; null when neither exists. */
    private ResourceLocation frameSprite(Difficulty d, String state) {
        for (String base : new String[] {d.frame(), layout.frame() == null ? null : layout.frame().toString()}) {
            if (base == null) continue;
            ResourceLocation id = ResourceLocation.tryParse(base);
            if (id == null) continue;
            ResourceLocation withState = id.withSuffix(state);
            if (exists(withState)) return withState;
            if (exists(id)) return id;
        }
        return null;
    }

    private boolean exists(ResourceLocation sprite) {
        return !minecraft.getGuiSprites().getSprite(sprite).contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    /** Height of a card's header row: its icon, its banner or one text line, whichever is tallest. */
    private int headerHeight(Difficulty d) {
        int h = LINE;
        if (layout.icons() && d.icon() != null) h = Math.max(h, iconSize(d.icon())[1]);
        ResourceLocation banner = bannerTexture(d);
        if (banner != null) h = Math.max(h, size(banner)[1]);
        return h;
    }

    /** The banner texture in the player's language, or null when there is none or it is missing. */
    private ResourceLocation bannerTexture(Difficulty d) {
        if (d.banner() == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(d.banner().resolve(lang()).getString());
        return id != null && SIZES.computeIfAbsent(id, ChooserScreen::readSize) != null ? id : null;
    }

    /** An item icon is 16x16; a texture icon is drawn at its own size. */
    private static int[] iconSize(String icon) {
        ResourceLocation id = ResourceLocation.tryParse(icon);
        if (id == null || !id.getPath().endsWith(".png")) return new int[] {ICON, ICON};
        int[] s = SIZES.computeIfAbsent(id, ChooserScreen::readSize);
        return s != null ? s : new int[] {ICON, ICON};
    }

    private static int[] size(ResourceLocation texture) {
        int[] s = SIZES.computeIfAbsent(texture, ChooserScreen::readSize);
        return s != null ? s : new int[] {ICON, ICON};
    }

    private static int[] readSize(ResourceLocation texture) {
        var res = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(texture);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            return new int[] {img.getWidth(), img.getHeight()};
        } catch (Exception e) {
            return null;
        }
    }

    /** Resource packs may change the textures: forget the sizes on every reload. */
    static void clearSizes() {
        SIZES.clear();
    }

    private void drawIcon(GuiGraphics g, String icon, int x, int y) {
        ResourceLocation id = ResourceLocation.tryParse(icon);
        if (id == null) return;
        if (id.getPath().endsWith(".png")) {
            int[] s = iconSize(icon);
            RenderSystem.enableBlend();
            g.blit(id, x, y, 0, 0, s[0], s[1], s[0], s[1]);
        } else {
            var item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR) g.renderItem(new ItemStack(item), x, y);
        }
    }

    private Card cardAt(double mx, double my) {
        if (my < gridY - 2 || my > gridY + gridH + 2) return null;
        for (Card c : cards) {
            int top = c.y - scroll;
            if (mx >= c.x && mx < c.x + cardW && my >= top && my < top + cardH) return c;
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (maxScroll == 0) return false;
        scroll = Mth.clamp(scroll - (int) (dy * 20), 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (canChoose && button == 0 && selected == null && pressed == null) {
            Card c = cardAt(mx, my);
            if (c != null) {
                if (layout.clickSound() != null) play(layout.clickSound());
                press(c.d);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Starts the press dip; render() opens the confirmation (or sends the choice) once it is over. */
    private void press(Difficulty d) {
        pressed = d;
        pressedAt = Util.getMillis();
        if (!anim.enabled() || anim.pressMs() <= 0) {
            pressed = null;
            choose(d);
        }
    }

    private void play(ResourceLocation sound) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(sound), 1.0F));
    }

    private void choose(Difficulty d) {
        if (!layout.confirm()) {
            send(d);
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                send(d);
                minecraft.setScreen(null);
            } else {
                minecraft.setScreen(this);
            }
        }, Component.translatable("pathbound.confirm.title", d.name().resolve(lang())), Component.translatable("pathbound.confirm.text")));
    }

    /** Without a confirmation the screen stays, showing the card as selected, until the server answers. */
    private void send(Difficulty d) {
        selected = d.id();
        ClientLogic.submitted = true;
        PacketDistributor.sendToServer(new ChooseModePayload(d.id()));
    }

    // ---- for scripted checks (clientValidation) ----

    int cardCount() {
        return cards.size();
    }

    /** Screen-space centre of a difficulty's card (scrolled into view first), or null. */
    int[] cardCenter(String id) {
        for (Card c : cards) {
            if (!c.d.id().equals(id)) continue;
            int top = c.y - scroll;
            if (top < gridY || top + cardH > gridY + gridH) {
                scroll = Mth.clamp(c.y - gridY, 0, maxScroll);
                top = c.y - scroll;
            }
            return new int[] {c.x + cardW / 2, top + cardH / 2};
        }
        return null;
    }

    /** The sprite a card is drawn with, or null for the plain fallback. */
    ResourceLocation frameOf(String id) {
        for (Card c : cards) if (c.d.id().equals(id)) return frameSprite(c.d, "");
        return null;
    }

    /** Treats a card as hovered when the real mouse is elsewhere (frames of the hover animation). */
    void forceHover(String id) {
        forcedHover = id;
    }

    /** The current lift of a card in GUI pixels. */
    double liftOf(String id) {
        for (Card c : cards) if (c.d.id().equals(id)) return c.lift;
        return 0;
    }

    long openedAt() {
        return openedAt;
    }
}
