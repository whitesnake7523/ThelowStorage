package com.example.thelowstorage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

public class StorageControlButtons extends Gui {
    private final Minecraft mc;
    private final FontRenderer fontRenderer;
    private static final int BTN_W = 140;
    private static final int BTN_H = 30;
    private static final float TEXT_SCALE = 1.5f;

    public StorageControlButtons(Minecraft mc, FontRenderer fontRenderer) {
        this.mc = mc;
        this.fontRenderer = fontRenderer;
    }

    public void draw(int mx, int my, float fixScale, int currentColumns) {
        ScaledResolution sr = new ScaledResolution(mc);
        // fixScale適用後の仮想画面幅
        float vWidth = sr.getScaledWidth() / fixScale;
        int x = (int)(vWidth - BTN_W - 10);
        int y1 = 5, y2 = y1 + BTN_H + 5;

        int vmx = (int) (mx / fixScale);
        int vmy = (int) (my / fixScale);

        boolean h1 = vmx >= x && vmx <= x + BTN_W && vmy >= y1 && vmy <= y1 + BTN_H;
        boolean h2 = vmx >= x && vmx <= x + BTN_W && vmy >= y2 && vmy <= y2 + BTN_H;

        GlStateManager.pushMatrix();
        GlStateManager.scale(fixScale, fixScale, 1.0f);
        GlStateManager.translate(0, 0, 500);

        drawRect(x, y1, x + BTN_W, y1 + BTN_H, h1 ? 0xAA55FF55 : 0xAA000000);
        drawScaledText(StorageGUI.isOverlayEnabled ? "Mod GUI: ON" : "Mod GUI: OFF", x, y1);

        if (StorageGUI.isOverlayEnabled) {
            drawRect(x, y2, x + BTN_W, y2 + BTN_H, h2 ? 0xAA55FFFF : 0xAA000000);
            drawScaledText("Layout: " + currentColumns, x, y2);
        }
        GlStateManager.popMatrix();
    }

    private void drawScaledText(String text, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
        int tx = (int) ((x + BTN_W / 2f) / TEXT_SCALE);
        int ty = (int) ((y + (BTN_H / 2f - 4 * TEXT_SCALE)) / TEXT_SCALE);
        fontRenderer.drawStringWithShadow(text, tx - fontRenderer.getStringWidth(text) / 2f, ty, 0xFFFFFF);
        GlStateManager.popMatrix();
    }

    public int handleClicks(int mx, int my, int button, int currentColumns) {
        if (button != 0) return currentColumns;

        ScaledResolution sr = new ScaledResolution(mc);
        float fixScale = 2.0f / sr.getScaleFactor();
        float vWidth = sr.getScaledWidth() / fixScale;

        int vmx = (int) (mx / fixScale);
        int vmy = (int) (my / fixScale);
        int x = (int)(vWidth - BTN_W - 10);
        int y1 = 5, y2 = y1 + BTN_H + 5;

        if (vmx >= x && vmx <= x + BTN_W && vmy >= y1 && vmy <= y1 + BTN_H) {
            StorageGUI.isOverlayEnabled = !StorageGUI.isOverlayEnabled;
            mc.thePlayer.playSound("random.click", 1.0f, 1.0f);
        } else if (StorageGUI.isOverlayEnabled && vmx >= x && vmx <= x + BTN_W && vmy >= y2 && vmy <= y2 + BTN_H) {
            currentColumns = (currentColumns >= 5) ? 3 : currentColumns + 1;
            mc.thePlayer.playSound("random.click", 1.0f, 1.2f);
        }
        return currentColumns;
    }
}