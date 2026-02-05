package com.example.thelowstorage;

import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import org.lwjgl.opengl.GL11;
public class StorageGUI extends GuiScreen {
    private float scrollY = 0.0F;
    private int maxScrollY = 0;
    private static int currentColumns = 3;
    private static final int BASE_ENTRY_WIDTH = 171;
    private static final int BASE_ENTRY_HEIGHT = 125;

    private long lastClickTime = 0L;
    public static boolean isOverlayEnabled = true;
    private GuiTextField searchField;
    private boolean isDraggingScrollBar = false;
    private long lastBackspaceTime = 0L;
    private static final long BACKSPACE_COOLDOWN = 100L;
    private static final float GUI_HEIGHT_PERCENT = 0.70f;
    private static final int INV_WIDTH = 176;
    private static final int INV_HEIGHT = 90;


    private int lastClickSlotId = -1;
    private int lastClickButton = -1;
    private int hoveredSlotId = -1;
    private String hoveredStorageName = null;
    private int lastDragSlotId = -1; // ドラッグ操作で最後にアイテムを置いたスロットID

    @Override
    public void initGui() {
        super.initGui();
        // Configから保存されている値を読み込んで同期する
        currentColumns = ConfigHandler.currentColumns;
        isOverlayEnabled = ConfigHandler.isOverlayEnabled;
    }

    // --- 新規追加: インベントリ用の固定スケール計算 ---
    private float getInventoryScale() {
        ScaledResolution sr = new ScaledResolution(this.mc);
        // 画面の実高さ (ピクセル)
        double actualHeight = sr.getScaledHeight_double() * sr.getScaleFactor();

        // 実高さの25%をターゲットとする
        double targetHeight = actualHeight * 0.25;

        // インベントリの基準高さ(約90px)が、ターゲット高さになる倍率を求める
        // GuiScreenの論理座標系に合わせるため scaleFactor で割る
        return (float) (targetHeight / INV_HEIGHT) / sr.getScaleFactor();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.renderFullGUI(mouseX, mouseY, (GuiChest)null);
    }

    private float getAbsoluteScaleFactor() {
        if (!ConfigHandler.useAbsoluteScaling) return 1.0F;
        ScaledResolution sr = new ScaledResolution(this.mc);
        return 2.0F / (float)sr.getScaleFactor();
    }

    private float getFillWidthScale() {
        float absScale = getAbsoluteScaleFactor();
        float targetWidth = (this.width / absScale) * 0.8F;
        float baseRequiredWidth = currentColumns * BASE_ENTRY_WIDTH;
        return targetWidth / baseRequiredWidth;
    }

    private float getTotalScale() {
        return getFillWidthScale() * getAbsoluteScaleFactor();
    }

    public void updateSearchField() {
        if (this.searchField != null) {
            this.searchField.updateCursorCounter();
        }
    }

    public int getGuiHeightLimit() {
        return (int)(this.height * GUI_HEIGHT_PERCENT);
    }

    public GuiTextField getSearchField() {
        return this.searchField;
    }

    public void onGuiOpened() {
        // 【重要】スイッチを入れ直すことで、OS側のIMEコンテキストを叩き起こす
        Keyboard.enableRepeatEvents(false);
        Keyboard.enableRepeatEvents(true);

        // 検索バーにフォーカスを当てて入力待機状態にする
        if (this.searchField != null) {
            this.searchField.setFocused(false);
        }
    }
    public void onGuiClosed() {
        // キーリピートを無効化（これを忘れると移動に支障が出ることがある）
        Keyboard.enableRepeatEvents(false);

        // 検索バーのフォーカスを外す
        if (this.searchField != null) {
            this.searchField.setFocused(false);
        }

        // ドラッグ状態の解除
        this.isDraggingScrollBar = false;
    }

    private boolean isEnderChest(GuiChest parent) {
        if (parent == null || parent.inventorySlots == null) return false;
        IInventory inv = ((ContainerChest)parent.inventorySlots).getLowerChestInventory();
        String title = inv.getDisplayName().getUnformattedText();
        // タイトルが「エンダーチェスト」（念のため英語名も）と一致するか確認
        return title.equals("エンダーチェスト") || title.equals("Ender Chest");
    }

    public void renderFullGUI(int mouseX, int mouseY, GuiChest parent) {
        int bottomLimit = getGuiHeightLimit();
        float absScale = getAbsoluteScaleFactor();
        float totalScale = getTotalScale();
        float fillScale = getFillWidthScale();

        // 仮想座標の計算
        float vWidthAbs = (float)this.width / absScale;
        float vHeightAbs = (float)this.height / absScale;

        // --- レイアウト設定 ---
        int searchW = (int)(vWidthAbs * 0.50f);
        int searchH = (int)(vHeightAbs * 0.04f);
        float searchY = vHeightAbs * 0.01f;
        float itemsStartY = searchY + (float)searchH + (vHeightAbs * 0.05f);

        int btnW = (int)(vWidthAbs * 0.09f);
        int btnH = (int)(vHeightAbs * 0.04f);

        // --- 1. 背景とメインコンテンツの描画 ---
        if (isOverlayEnabled || parent == null) {

            // 背景描画 (0x80101010: 半透明の黒)
            drawRect(0, 0, this.width, this.height, 0x80101010);
            drawRect(0, bottomLimit - 1, this.width, bottomLimit, 0xFF555555);

            if (this.searchField == null) {
                this.searchField = new GuiTextField(0, this.fontRendererObj, 0, 0, searchW, searchH);
                this.searchField.setMaxStringLength(50);
                // 初期化時はフォーカスを持たせない (onGuiOpenedで制御)
                this.searchField.setFocused(false);
            }

            // 【修正】ここで Keyboard.enableRepeatEvents(true) を呼ぶと
            // 日本語入力のバグ原因になるため削除しました。
            // (updateSearchField と onGuiOpened に任せます)

            this.searchField.width = searchW;
            this.searchField.height = searchH;

            // クリップ処理 (Scissor)
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            ScaledResolution sr = new ScaledResolution(this.mc);
            int scale = sr.getScaleFactor();
            int cutHeight = bottomLimit * scale;
            GL11.glScissor(0, this.mc.displayHeight - cutHeight, this.mc.displayWidth, cutHeight);

            // 検索バーの描画
            GlStateManager.pushMatrix();
            GlStateManager.scale(absScale, absScale, 1.0F);
            this.searchField.xPosition = (int)((vWidthAbs - searchW) / 2);
            this.searchField.yPosition = (int)searchY;
            this.searchField.drawTextBox();

            // クリアボタン(X)
            int bx = this.searchField.xPosition + this.searchField.width + 5;
            int by = this.searchField.yPosition;
            drawRect(bx, by, bx + 20, by + searchH, 0xAA880000);
            this.fontRendererObj.drawStringWithShadow("X", bx + 7, by + (searchH / 2 - 4), 16777215);
            GlStateManager.popMatrix();

            // ページ情報の取得
            int curServerPage = -1;
            boolean isCurrentEnderChest = false; // エンダーチェスト判定フラグ

            if (parent != null) {
                IInventory inv = ((ContainerChest)parent.inventorySlots).getLowerChestInventory();
                String title = inv.getDisplayName().getUnformattedText();

                // ページ判定
                Matcher m = Pattern.compile("倉庫 -(\\d+)ページ目-").matcher(title);
                if (m.find()) {
                    curServerPage = Integer.parseInt(m.group(1));
                } else if (title.equals("倉庫")) {
                    curServerPage = 1;
                }

                // エンダーチェスト判定
                if (title.equals("エンダーチェスト") || title.equals("Ender Chest")) {
                    isCurrentEnderChest = true;
                }
            }

            List<Entry<String, ItemStack[]>> entries = StorageCache.getSortedEntries();
            String query = this.searchField.getText().toLowerCase();

            // スクロール処理
            this.handleScrollBarDragging(mouseY, bottomLimit);
            this.scrollY = Math.max(0.0F, Math.min(this.scrollY, (float)this.maxScrollY));

            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0F, 0.0F, 100.0F);
            GlStateManager.scale(totalScale, totalScale, 1.0F);

            // スケーリングされた仮想座標
            int smx = (int)(mouseX / totalScale);
            int smy = (int)(mouseY / totalScale);
            float sw = (float)this.width / totalScale;
            float sh = (float)bottomLimit / totalScale;

            int gridWidth = currentColumns * BASE_ENTRY_WIDTH;
            float startX = (sw - (float)gridWidth) / 2.0f;

            // アイテム描画開始位置
            float currentY = (itemsStartY / fillScale) - this.scrollY;

            // エントリ描画ループ
            for(int i = 0; i < entries.size(); ++i) {
                Entry<String, ItemStack[]> entry = entries.get(i);
                int col = i % currentColumns;
                int row = i / currentColumns;
                float xPos = startX + (float)col * BASE_ENTRY_WIDTH;
                float yPos = currentY + (float)row * BASE_ENTRY_HEIGHT;

                // 描画範囲内なら描画
                if (yPos + BASE_ENTRY_HEIGHT >= 0 && yPos <= sh) {
                    boolean isCurrentPage = (StorageCache.getPageFromPriority(StorageCache.getPriority(entry.getKey())) == curServerPage);
                    this.drawEntry(entry, (int)xPos, (int)yPos, query, isCurrentPage);

                    // ★エンダーチェスト対策: 空白領域(4段目以降)のマスク描画
                    String entryName = entry.getKey().replaceAll("§[0-9a-fk-or]", "").replaceAll("[\\\\/:*?\"<>|]", "_");
                    if (isCurrentEnderChest && entryName.equals(StorageHandler.getActiveStorageName())) {
                        int maskStartY = (int)yPos + (3 * 18); // 3段目(有効)の下から開始
                        // 半透明の黒で塗りつぶし、操作不能であることを視覚的に示す
                        drawRect((int)xPos, maskStartY, (int)xPos + 162, maskStartY + (3 * 18), 0xA0000000);
                    }
                }
            }
            GlStateManager.popMatrix();

            // 最大スクロール量の更新
            int totalRows = (entries.size() + currentColumns - 1) / currentColumns;
            float topOffsetVirtual = itemsStartY / fillScale;
            this.maxScrollY = (int)Math.max(0, (topOffsetVirtual + totalRows * BASE_ENTRY_HEIGHT) - sh);

            this.drawScrollBar(bottomLimit);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            // ツールチップ
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0F, 0.0F, 100.0F);
            GlStateManager.scale(totalScale, totalScale, 1.0F);
            this.renderHoveredTooltips(smx, smy, (int)startX, (int)currentY, entries, (int)sh);
            GlStateManager.popMatrix();

            // プレイヤーインベントリ
            if (parent != null) {
                this.drawPlayerInventory(mouseX, mouseY, parent, bottomLimit);
            }
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        // コントロールボタン描画
        this.drawControlButtons(mouseX, mouseY, absScale, (int)vWidthAbs, (int)vHeightAbs, btnW, btnH);

        // 掴んでいるアイテムの描画 (最前面)
        if (this.mc.thePlayer.inventory.getItemStack() != null) {
            ItemStack stack = this.mc.thePlayer.inventory.getItemStack();
            float prevZ = this.mc.getRenderItem().zLevel;
            this.mc.getRenderItem().zLevel = 500.0F;
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.pushMatrix();
            int x = mouseX - 8;
            int y = mouseY - 8;
            this.mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
            this.mc.getRenderItem().renderItemOverlays(this.fontRendererObj, stack, x, y);
            GlStateManager.popMatrix();
            RenderHelper.disableStandardItemLighting();
            this.mc.getRenderItem().zLevel = prevZ;
        }
    }

    private void drawEntry(Entry<String, ItemStack[]> entry, int x, int y, String query, boolean isCurrentPage) {
        ItemStack[] items = entry.getValue();
        // 基本の行数計算
        int rows = (items.length <= 27) ? 3 : 6;
        int bgHeight = rows * 18 + 2;

        String entryName = entry.getKey().replaceAll("§[0-9a-fk-or]", "").replaceAll("[\\\\/:*?\"<>|]", "_");
        boolean isOperatable = entryName.equals(StorageHandler.getActiveStorageName());

        // --- 検索ヒット判定 ---
        boolean hasMatch = false;
        if (!query.isEmpty()) {
            for (ItemStack item : items) {
                if (item != null && isItemMatched(item, query)) {
                    hasMatch = true;
                    break;
                }
            }
        }

        // --- 枠の描画 ---
        if (hasMatch) {
            drawRect(x - 3, y - 3, x + 165, y + bgHeight + 1, 0xAAFF0000);
        } else if (isOperatable) {
            drawRect(x - 3, y - 3, x + 165, y + bgHeight + 1, 0x6600FF00);
        } else if (isCurrentPage) {
            drawRect(x - 3, y - 3, x + 165, y + bgHeight + 1, 0x3355FF55);
        }

        drawRect(x - 2, y - 2, x + 164, y + bgHeight, 0xCC000000);
        this.fontRendererObj.drawStringWithShadow("§6" + entry.getKey(), (float)x, (float)(y - 12), 16777215);

        // リアルタイムインベントリの取得
        IInventory liveInv = null;
        boolean isEnderChest = false; // エンダーチェストかどうか
        if (isOperatable && this.mc.currentScreen instanceof GuiChest) {
            GuiChest gc = (GuiChest)this.mc.currentScreen;
            liveInv = ((ContainerChest)gc.inventorySlots).getLowerChestInventory();
            isEnderChest = isEnderChest(gc); // ヘルパーメソッドを利用
        }

        RenderHelper.enableGUIStandardItemLighting();

        // ★【重要】ループ回数の決定
        // エンダーチェストの場合は、データが54個あっても強制的に「27個(3行)」で描画を打ち切る
        int maxLoop = (isEnderChest && isOperatable) ? 27 : 54;

        for(int slot = 0; slot < maxLoop; ++slot) {
            if (slot >= rows * 9) break;
            int sX = x + (slot % 9) * 18;
            int sY = y + (slot / 9) * 18;

            ItemStack stack = (slot < items.length) ? items[slot] : null;

            if (liveInv != null && slot < liveInv.getSizeInventory()) {
                ItemStack liveStack = liveInv.getStackInSlot(slot);
                if (liveStack != null) {
                    String dName = liveStack.getDisplayName();
                    if (dName.contains("一覧画面に戻る") || dName.contains("ここにはアイテムを置けません")) {
                        liveStack = null;
                    }
                }
                stack = liveStack;
            }

            drawRect(sX, sY, sX + 16, sY + 16, 0x33FFFFFF);

            if (stack != null && !query.isEmpty() && isItemMatched(stack, query)) {
                drawRect(sX - 1, sY - 1, sX + 17, sY + 17, 0xAAFF0000);
            }

            if (stack != null) {
                if (!isOperatable) GlStateManager.color(0.6f, 0.6f, 0.6f, 1.0f);
                this.mc.getRenderItem().renderItemAndEffectIntoGUI(stack, sX, sY);
                this.mc.getRenderItem().renderItemOverlays(this.fontRendererObj, stack, sX, sY);
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }
        RenderHelper.disableStandardItemLighting();
    }
    /**
     * マウス入力を処理し、Mod GUI領域内またはMod有効時の全イベントを制御します。
     * return true を返すことで、バニラ(GuiChest)へのイベント伝播を遮断します。
     */
    /**
     * マウス入力を処理し、Mod GUI領域内またはMod有効時の全イベントを制御します。
     * ConfigHandlerへの保存処理を追加済みです。
     */
    public boolean handleMouseInputs(int mouseX, int mouseY, int mouseButton, GuiChest parent) {
        boolean isPressed = org.lwjgl.input.Mouse.getEventButtonState();
        int guiLimit = getGuiHeightLimit();
        float absScale = getAbsoluteScaleFactor();
        float totalScale = getTotalScale();
        float fillScale = getFillWidthScale();

        float vWidthAbs = (float)this.width / absScale;
        float vHeightAbs = (float)this.height / absScale;
        float vmx = (float)mouseX / absScale;
        float vmy = (float)mouseY / absScale;

        int btnW = (int)(vWidthAbs * 0.09f);
        int btnH = (int)(vHeightAbs * 0.04f);
        int bx = (int)vWidthAbs - btnW - 12;

        // --- Mod GUI が OFF の時の処理（ONにするボタン） ---
        if (!isOverlayEnabled) {
            if (isPressed && mouseButton == 0 && vmx >= bx && vmx <= bx + btnW && vmy >= 5 && vmy <= 5 + btnH) {
                isOverlayEnabled = true;
                ConfigHandler.setOverlayEnabled(true); // ★追加: 設定を保存
                this.mc.thePlayer.playSound("random.click", 1.0F, 1.0F);
                return true;
            }
            return false;
        }

        int dWheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dWheel != 0) {
            this.scrollY -= (float)dWheel * (0.3F / totalScale);
            this.scrollY = Math.max(0.0F, Math.min(this.scrollY, (float)this.maxScrollY));
            return true;
        }

        if (!isPressed || mouseButton == -1) {
            if (mouseButton == 0) this.isDraggingScrollBar = false;
            return true;
        }

        // --- Mod GUI が ON の時のコントロールボタン処理 ---
        if (mouseButton == 0 && vmx >= bx && vmx <= bx + btnW) {
            if (vmy >= 5 && vmy <= 5 + btnH) {
                // Mod GUI OFF ボタン
                isOverlayEnabled = false;
                ConfigHandler.setOverlayEnabled(false); // ★追加: 設定を保存
                this.mc.thePlayer.playSound("random.click", 1.0F, 0.8F);
                return true;
            } else if (vmy >= 10 + btnH && vmy <= 10 + btnH * 2) {
                // Layout変更ボタン
                currentColumns = (currentColumns >= 5) ? 3 : currentColumns + 1;
                ConfigHandler.setColumns(currentColumns); // ★追加: 設定を保存
                this.mc.thePlayer.playSound("random.click", 1.0F, 1.2F);
                return true;
            }
        }

        // インベントリクリック (GUI下部のプレイヤーインベントリ)
        if (mouseY > guiLimit) {
            if (parent != null && (mouseButton == 0 || mouseButton == 1)) {
                handlePlayerInventoryClick(mouseX, mouseY, mouseButton, parent, guiLimit);
            }
            return true;
        }

        // 検索バー
        if (this.searchField != null) {
            int searchW = (int)(vWidthAbs * 0.50f);
            int searchH = (int)(vHeightAbs * 0.04f);
            float searchY = vHeightAbs * 0.01f;
            int sx = (int)((vWidthAbs - searchW) / 2);
            int sy = (int)searchY;

            // Xボタン（クリア）
            if (mouseButton == 0 && vmx >= sx + searchW + 5 && vmx <= sx + searchW + 25 && vmy >= sy && vmy <= sy + searchH) {
                this.searchField.setText("");
                this.scrollY = 0.0F;
                this.searchField.setFocused(true);
                this.mc.thePlayer.playSound("random.click", 1.0F, 1.2F);
                org.lwjgl.input.Keyboard.enableRepeatEvents(false);
                org.lwjgl.input.Keyboard.enableRepeatEvents(true);
                return true;
            }

            this.searchField.mouseClicked((int)vmx, (int)vmy, mouseButton);
            if (this.searchField.isFocused()) {
                org.lwjgl.input.Keyboard.enableRepeatEvents(false);
                org.lwjgl.input.Keyboard.enableRepeatEvents(true);
                return true;
            }
        }

        // スクロールバー
        if (this.maxScrollY > 0 && mouseButton == 0) {
            int barW = (int)(this.width * 0.05f);
            int barX = this.width - barW - 2;
            int barY = (int)(guiLimit * 0.15f);
            int barH = guiLimit - barY - 10;

            if (mouseX >= barX && mouseX <= barX + barW && mouseY >= barY && mouseY <= barY + barH) {
                this.isDraggingScrollBar = true;
                return true;
            }
        }

        // アイテムスロット
        if (mouseButton == 0 || mouseButton == 1) {
            float smx = (float)mouseX / totalScale;
            float smy = (float)mouseY / totalScale;
            float vWidthTotal = (float)this.width / totalScale;
            int gridWidth = currentColumns * BASE_ENTRY_WIDTH;
            float startX = (vWidthTotal - (float)gridWidth) / 2.0f;

            float searchH_F = (vHeightAbs * 0.04f);
            float itemsStartY = (vHeightAbs * 0.01f) + searchH_F + (vHeightAbs * 0.05f);
            float currentY = (itemsStartY / fillScale) - this.scrollY;

            List<java.util.Map.Entry<String, ItemStack[]>> entries = StorageCache.getSortedEntries();
            boolean isCurrentEnderChest = isEnderChest(parent);

            for(int i = 0; i < entries.size(); ++i) {
                java.util.Map.Entry<String, ItemStack[]> entry = entries.get(i);

                // 通常の行数(rows)と、クリック有効な行数(logicalRows)を定義
                int rows = (entry.getValue().length <= 27) ? 3 : 6;

                // エンダーチェストで操作中なら、3行目までしかクリックさせない
                boolean isActiveEnder = isCurrentEnderChest && entry.getKey().replaceAll("§[0-9a-fk-or]", "").replaceAll("[\\\\/:*?\"<>|]", "_").equals(StorageHandler.getActiveStorageName());
                int logicalRows = isActiveEnder ? 3 : rows;

                float xPos = startX + (float)(i % currentColumns) * BASE_ENTRY_WIDTH;
                float yPos = currentY + (float)(i / currentColumns) * BASE_ENTRY_HEIGHT;

                // 1. 有効なエリア(logicalRows)内のみクリックを許可
                if (smx >= xPos && smx <= xPos + 162 && smy >= yPos && smy <= yPos + (logicalRows * 18)) {
                    processItemClick(entry, (int)smx, (int)smy, (int)xPos, (int)yPos, logicalRows, mouseButton, parent);
                    return true;
                }

                // 2. 空白部分のガード
                // 有効範囲(logicalRows)より下だが、全体の枠(rows)の中をクリックした場合 = 虚無クリック
                if (isActiveEnder && rows > logicalRows) {
                    if (smx >= xPos && smx <= xPos + 162 && smy > yPos + (logicalRows * 18) && smy <= yPos + (rows * 18)) {
                        return true; // イベントを消費して、下のインベントリへの貫通を防ぐ
                    }
                }
            }
        }

        return true;
    }


    private void processItemClick(Entry<String, ItemStack[]> entry, int smx, int smy, int xPos, int yPos, int rows, int mouseButton, GuiChest parent) {
        String entryName = entry.getKey().replaceAll("§[0-9a-fk-or]", "").replaceAll("[\\\\/:*?\"<>|]", "_");

        if (entryName.equals(StorageHandler.getActiveStorageName()) && parent != null) {
            int slotX = (smx - xPos) / 18;
            int slotY = (smy - yPos) / 18;
            int slotIdx = slotX + (slotY * 9);

            if (slotIdx >= 0 && slotIdx < rows * 9) {

                long currentTime = Minecraft.getSystemTime();
                int mode = 0;

                ItemStack heldStack = this.mc.thePlayer.inventory.getItemStack();

                // 1. Shiftキー (クイック移動)
                if (isShiftKeyDown()) {
                    mode = 1;
                }
                // 2. 左ダブルクリック (スタック回収)
                else if (mouseButton == 0
                        && heldStack != null
                        && this.lastClickSlotId == slotIdx
                        && this.lastClickButton == mouseButton
                        && currentTime - this.lastClickTime < 150L) {
                    mode = 6;
                }
                // 3. 右クリック
                else if (mouseButton == 1) {
                    // ★修正: 右クリックの配置処理は handleRightClickDrag (ドラッグ監視) に全て任せるため、
                    // ここでのクリック処理は行わずに終了します。これで重複配置を防ぎます。
                    return;
                }

                // 履歴更新
                this.lastClickSlotId = slotIdx;
                this.lastClickTime = currentTime;
                this.lastClickButton = mouseButton;

                this.mc.playerController.windowClick(parent.inventorySlots.windowId, slotIdx, mouseButton, mode, this.mc.thePlayer);
                ThelowStorageMod.storageHandler.triggerCacheUpdate();
            }
        } else if (parent != null) {
            if (StorageHandler.getActiveStorageName() != null) {
                this.clickReturnButton(parent);
            }
            this.syncClick(entry.getKey(), parent);
        }
    }
    // --- 新規追加メソッド ---
    /**
     * 現在のインベントリから「一覧画面に戻る」という名前のアイテムを探してクリックします。
     * @return クリックに成功したら true
     */
    private boolean clickReturnButton(GuiChest parent) {
        IInventory inv = ((ContainerChest)parent.inventorySlots).getLowerChestInventory();
        String activeName = StorageHandler.getActiveStorageName();

        for (int i = 0; i < inv.getSizeInventory(); ++i) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null && s.getDisplayName().contains("一覧画面に戻る")) {
                if (activeName != null) {
                    NBTUtils.saveInventoryToNBT(inv, activeName);
                }

                sendSilentClick(parent, i);

                // StorageHandler.resetActiveStorage(); は呼ばない、あるいは
                // 後続のTickで自然に切り替わるのを待つことで描画を維持する
                return true;
            }
        }
        return false;
    }

    private void renderHoveredTooltips(int mx, int my, int sx, int cy, List<Entry<String, ItemStack[]>> entries, int sh) {
        for(int i = 0; i < entries.size(); ++i) {
            Entry<String, ItemStack[]> entry = entries.get(i);
            ItemStack[] items = entry.getValue();
            int rows = (items.length <= 27) ? 3 : 6;
            int xPos = sx + (i % currentColumns) * BASE_ENTRY_WIDTH;
            int yPos = cy + (i / currentColumns) * BASE_ENTRY_HEIGHT;

            if (yPos + (rows * 18) >= 0 && yPos <= sh) {
                for(int slot = 0; slot < items.length; ++slot) {
                    int sX = xPos + (slot % 9) * 18;
                    int sY = yPos + (slot / 9) * 18;
                    if (items[slot] != null && mx >= sX && mx <= sX + 16 && my >= sY && my <= sY + 16) {
                        this.renderToolTip(items[slot], mx, my);
                    }
                }
            }
        }
    }
    private void drawControlButtons(int mx, int my, float absScale, int vWidth, int vHeight, int bW, int bH) {
        int x = vWidth - bW - 12;
        int y1 = 5;
        int y2 = y1 + bH + 5;
        int vmx = (int)(mx / absScale);
        int vmy = (int)(my / absScale);
        boolean h1 = vmx >= x && vmx <= x + bW && vmy >= y1 && vmy <= y1 + bH;
        boolean h2 = vmx >= x && vmx <= x + bW && vmy >= y2 && vmy <= y2 + bH;

        GlStateManager.pushMatrix();
        GlStateManager.scale(absScale, absScale, 1.0F);
        GlStateManager.translate(0.0F, 0.0F, 500.0F);
        drawRect(x, y1, x + bW, y1 + bH, h1 ? -1437204651 : -1442840576);
        this.drawCenteredString(this.fontRendererObj, isOverlayEnabled ? "Mod GUI: ON" : "Mod GUI: OFF", x + bW / 2, y1 + (bH / 2 - 4), 16777215);
        if (isOverlayEnabled) {
            drawRect(x, y2, x + bW, y2 + bH, h2 ? -1437204481 : -1442840576);
            this.drawCenteredString(this.fontRendererObj, "Layout: " + currentColumns, x + bW / 2, y2 + (bH / 2 - 4), 16777215);
        }
        GlStateManager.popMatrix();
    }

    private boolean isItemMatched(ItemStack stack, String query) {
        String name = stack.getDisplayName().replaceAll("§[0-9a-fk-or]", "").toLowerCase();
        return name.contains(query);
    }
    private void drawScrollBar(int bottomLimit) {
        // 【修正】横幅を画面の10%に設定
        int barW = (int)(this.width * 0.05f);
        int barX = this.width - barW - 2;

        int barY = (int)(bottomLimit * 0.15f);
        int barH = bottomLimit - barY - 10;

        // 背景
        drawRect(barX, barY, barX + barW, barY + barH, 0x80000000);

        if (this.maxScrollY > 0) {
            // つまみ（Thumb）の描画
            int thumbH = Math.max(20, (int)((float)barH * ((float)barH / (float)(this.maxScrollY + barH))));
            int thumbY = barY + (int)((float)this.scrollY / (float)this.maxScrollY * (float)(barH - thumbH));
            drawRect(barX, thumbY, barX + barW, thumbY + thumbH, 0xFF888888);
            drawRect(barX, thumbY, barX + barW - 1, thumbY + thumbH - 1, 0xFFAAAAAA);
        }
    }

    private void handleScrollBarDragging(int mouseY, int bottomLimit) {
        if (!Mouse.isButtonDown(0)) { this.isDraggingScrollBar = false; return; }
        if (this.isDraggingScrollBar && this.maxScrollY > 0) {
            int barY = (int)(bottomLimit * 0.15f);
            int barH = bottomLimit - barY - 10;
            int handleH = 20;
            if (barH - handleH > 0) {
                float relative = (float)(mouseY - barY - handleH / 2) / (float)(barH - handleH);
                this.scrollY = Math.max(0.0F, Math.min(relative * (float)this.maxScrollY, (float)this.maxScrollY));
            }
        }
    }

    // StorageGUI.java

    public boolean keyTyped(char typedChar, int keyCode, GuiChest parent) {
        // 1. 検索バー処理 (フォーカス時は最優先)
        if (this.searchField != null && this.searchField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.searchField.setFocused(false);
                return true;
            }
            boolean consumed = this.searchField.textboxKeyTyped(typedChar, keyCode);
            if (consumed) this.scrollY = 0.0F;
            return consumed;
        }

        // 2. ホットバーキーの特定
        int hotbarSlot = -1;
        for (int i = 0; i < 9; ++i) {
            if (keyCode == this.mc.gameSettings.keyBindsHotbar[i].getKeyCode()) {
                hotbarSlot = i;
                break;
            }
        }

        boolean isNumKey = (hotbarSlot != -1);
        boolean isDropKey = (keyCode == this.mc.gameSettings.keyBindDrop.getKeyCode());

        // 関係ないキーはバニラへ
        if (!isNumKey && !isDropKey) return false;

        // --- マウス座標 ---
        int mouseX = Mouse.getX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getY() * this.height / this.mc.displayHeight - 1;

        if (parent.inventorySlots == null) return false;
        int windowId = parent.inventorySlots.windowId;
        int guiLimit = getGuiHeightLimit();

        // --- A. Mod GUI (倉庫側) の判定 ---
        if (mouseY <= guiLimit) {
            float totalScale = getTotalScale();
            float fillScale = getFillWidthScale();
            float vHeightAbs = (float)this.height / getAbsoluteScaleFactor();
            int searchH = (int)(vHeightAbs * 0.04f);
            float itemsStartY = (vHeightAbs * 0.01f) + (float)searchH + (vHeightAbs * 0.05f);
            float currentY = (itemsStartY / fillScale) - this.scrollY;

            float sw = (float)this.width / totalScale;
            float startX = (sw - (float)(currentColumns * BASE_ENTRY_WIDTH)) / 2.0f;

            float smx = (float)mouseX / totalScale;
            float smy = (float)mouseY / totalScale;

            List<Entry<String, ItemStack[]>> entries = StorageCache.getSortedEntries();
            boolean isCurrentEnderChest = isEnderChest(parent);

            for (int i = 0; i < entries.size(); ++i) {
                Entry<String, ItemStack[]> entry = entries.get(i);
                int rows = (entry.getValue().length <= 27) ? 3 : 6;
                String cleanName = entry.getKey().replaceAll("§[0-9a-fk-or]", "").replaceAll("[\\\\/:*?\"<>|]", "_");
                boolean isActive = cleanName.equals(StorageHandler.getActiveStorageName());
                int logicalRows = (isCurrentEnderChest && isActive) ? 3 : rows;

                float xPos = startX + (float)(i % currentColumns) * BASE_ENTRY_WIDTH;
                float yPos = currentY + (float)(i / currentColumns) * BASE_ENTRY_HEIGHT;

                // 枠内判定
                if (smx >= xPos && smx <= xPos + 162 && smy >= yPos && smy <= yPos + (logicalRows * 18)) {
                    int col = (int)((smx - xPos) / 18);
                    int row = (int)((smy - yPos) / 18);
                    int slotIndex = col + (row * 9);

                    // 配列範囲チェック
                    if (slotIndex >= 0 && slotIndex < entry.getValue().length) {
                        boolean hasItem = (entry.getValue()[slotIndex] != null);

                        if (isNumKey) {
                            // ★修正点: 数字キー移動は「空スロット」に対しても有効であるべきなので
                            // entry.getValue() != null チェックは行わない
                            this.handleStorageInteraction(entry.getKey(), slotIndex, hotbarSlot, 2, parent);
                            ThelowStorageMod.storageHandler.triggerCacheUpdate();
                            return true;
                        } else if (isDropKey && hasItem) {
                            // ドロップはアイテムがある場合のみ
                            int button = GuiScreen.isCtrlKeyDown() ? 1 : 0;
                            this.handleStorageInteraction(entry.getKey(), slotIndex, button, 4, parent);
                            ThelowStorageMod.storageHandler.triggerCacheUpdate();
                            return true;
                        }
                    }
                }
            }
        }
        // --- B. プレイヤーインベントリ側の判定 ---
        else {
            if (parent.inventorySlots instanceof ContainerChest) {
                ContainerChest container = (ContainerChest) parent.inventorySlots;
                int chestSize = container.getLowerChestInventory().getSizeInventory();
                float invScale = getInventoryScale();
                float screenW = this.width / invScale;
                float screenH = this.height / invScale;
                int startX = (int)((screenW - (9 * 18)) / 2);
                int startY = (int)(screenH - (4 * 18 + 4) - 10);
                int hotbarY = startY + 3 * 18 + 4;
                int vmx = (int)(mouseX / invScale);
                int vmy = (int)(mouseY / invScale);

                for (int i = 0; i < 36; i++) {
                    int x = startX + (i % 9) * 18;
                    int y = (i < 27) ? (startY + (i / 9) * 18) : hotbarY;

                    if (vmx >= x && vmx < x + 18 && vmy >= y && vmy < y + 18) {
                        int targetSlotIndex = chestSize + i;

                        if (isNumKey) {
                            this.mc.playerController.windowClick(windowId, targetSlotIndex, hotbarSlot, 2, this.mc.thePlayer);
                            return true;
                        } else if (isDropKey) {
                            // ★インライン化: 外部メソッドへの依存を削除して安全性を確保
                            int button = GuiScreen.isCtrlKeyDown() ? 1 : 0;
                            this.mc.playerController.windowClick(windowId, targetSlotIndex, button, 4, this.mc.thePlayer);
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
    private void processPlayerInvKey(int windowId, int slotIndex, int keyCode, boolean isNumKey, boolean isDropKey) {
        if (isNumKey) {
            // 数字キー/ホットバーキーによる入れ替え (Mode 2)
            // hotbarSlotの特定は呼び出し側のkeyTypedで行い、直接windowClickを呼ぶ方が確実なため、
            // keyTyped側で処理を完結させている場合は、このメソッド内では主にドロップを扱います。
        } else if (isDropKey) {
            // ドロップ操作 (Mode 4)
            // button: 0 = 1個捨てる, 1 = 1スタック捨てる (Ctrl押下時)
            int button = GuiScreen.isCtrlKeyDown() ? 1 : 0;
            this.mc.playerController.windowClick(windowId, slotIndex, button, 4, this.mc.thePlayer);
        }
    }
    // --- StorageGUI.java: handleKeyInput 全体 KeyTypedと統合済み---
//    public boolean handleKeyInput(char character, int key, int mouseX, int mouseY, GuiChest parent) {
//        // 1. 検索バーにフォーカスがある場合は入力を優先（数字も文字として扱う）
//        if (this.searchField != null && this.searchField.isFocused()) {
//            boolean isConsumed = this.searchField.textboxKeyTyped(character, key);
//            if (isConsumed) this.scrollY = 0.0F;
//            return isConsumed;
//        }
//
//        // 2. 数字キー (1-9) の判定
//        if (key >= Keyboard.KEY_1 && key <= Keyboard.KEY_9) {
//            int hotbarSlot = key - Keyboard.KEY_1; // 0 ～ 8 に変換
//
//            float totalScale = getTotalScale();
//            float fillScale = getFillWidthScale();
//            float smx = (float)mouseX / totalScale;
//            float smy = (float)mouseY / totalScale;
//
//            // レイアウト計算 (renderFullGUIと同期)
//            float vWidthAbs = (float)this.width / getAbsoluteScaleFactor();
//            float vHeightAbs = (float)this.height / getAbsoluteScaleFactor();
//            int searchH = (int)(vHeightAbs * 0.04f);
//            float itemsStartY = (vHeightAbs * 0.01f) + (float)searchH + (vHeightAbs * 0.05f);
//
//            List<java.util.Map.Entry<String, ItemStack[]>> entries = StorageCache.getSortedEntries();
//            float sw = (float)this.width / totalScale;
//            int gridWidth = currentColumns * BASE_ENTRY_WIDTH;
//            float startX = (sw - (float)gridWidth) / 2.0f;
//            float currentY = (itemsStartY / fillScale) - this.scrollY;
//
//            // ホバー中のスロットを特定
//            for(int i = 0; i < entries.size(); ++i) {
//                java.util.Map.Entry<String, ItemStack[]> entry = entries.get(i);
//                int rows = (entry.getValue().length <= 27) ? 3 : 6;
//                float xPos = startX + (float)(i % currentColumns) * BASE_ENTRY_WIDTH;
//                float yPos = currentY + (float)(i / currentColumns) * BASE_ENTRY_HEIGHT;
//
//                if (smx >= xPos && smx <= xPos + 162 && smy >= yPos && smy <= yPos + (rows * 18)) {
//                    // 数字キーでのクリック処理 (mode 2 がホットバー入れ替え)
//                    // mouseButton の代わりにホットバースロット番号を渡す仕組みを想定
//                    processNumberKeyClick(entry, (int)smx, (int)smy, (int)xPos, (int)yPos, rows, hotbarSlot, parent);
//                    return true;
//                }
//            }
//        }
//        return false;
//    }

//    // 数字キー専用のクリック処理
//    private void processNumberKeyClick(java.util.Map.Entry<String, ItemStack[]> entry, int smx, int smy, int xPos, int yPos, int rows, int hotbarSlot, GuiChest parent) {
//        // マウス位置からどの内部スロット (0-26 または 0-53) かを計算
//        int col = (smx - xPos) / 18;
//        int row = (smy - yPos) / 18;
//        int slotIndex = col + row * 9;
//
//        if (slotIndex >= 0 && slotIndex < entry.getValue().length) {
//            // パケット送信またはウィンドウクリック処理
//            // mode: 2 (Hotbar swap), usedButton: hotbarSlot (0-8)
//            this.handleStorageInteraction(entry.getKey(), slotIndex, hotbarSlot, 2, parent);
////            this.mc.thePlayer.playSound("random.click", 1.0F, 1.0F);
//        }
//    }

    /**
     * ストレージ（コンテナ）のスロットに対して実際のアクションを実行します。
     * 修正: 小チェスト(27)を開いている際に、キャッシュ上の28番目以降(大チェスト用の枠)をクリックしても
     * プレイヤーインベントリが反応しないようにガード処理を追加しました。
     */
    /**
     * ストレージ（コンテナ）のスロットに対して実際のアクションを実行します。
     * 修正: オブジェクト判定ではなく、数値的なサイズ比較で確実に範囲外アクセスを遮断します。
     */
    private void handleStorageInteraction(String storageName, int slotId, int clickedButton, int mode, GuiChest parent) {
        // 1. 名前チェック（操作権限の確認）
        String activeName = StorageHandler.getActiveStorageName();
        if (activeName == null || !activeName.equals(storageName)) {
            return;
        }

        // 2. 【重要】コンテナサイズによる物理ガード
        // エンダーチェスト(27スロット)を開いている時に、Mod GUI上の28番目などをクリックすると、
        // バニラの仕様では「プレイヤーインベントリ」が反応してしまいます。
        // これを防ぐため、実際のチェストサイズ以上のIDへのアクセスはここで強制終了します。
        if (parent.inventorySlots instanceof ContainerChest) {
            ContainerChest container = (ContainerChest) parent.inventorySlots;
            IInventory lowerInv = container.getLowerChestInventory();

            if (lowerInv != null) {
                int actualChestSize = lowerInv.getSizeInventory(); // 小:27, 大:54

                // クリックしようとしているIDが、実際の箱のサイズを超えていたら無視する
                if (slotId >= actualChestSize) {
                    return;
                }
            }
        }

        // 3. クリック送信
        int windowId = parent.inventorySlots.windowId;
        this.mc.playerController.windowClick(
                windowId,
                slotId,
                clickedButton,
                mode,
                this.mc.thePlayer
        );
    }
    private void syncClick(String name, GuiChest parent) {
        IInventory inv = ((ContainerChest)parent.inventorySlots).getLowerChestInventory();
        int curPage = 1;
        Matcher m = Pattern.compile("倉庫 -(\\d+)ページ目-").matcher(inv.getDisplayName().getUnformattedText());
        if (m.find()) curPage = Integer.parseInt(m.group(1));

        int targetPage = StorageCache.getPageFromPriority(StorageCache.getPriority(name));

        if (curPage == targetPage) {
            for(int i = 0; i < inv.getSizeInventory(); ++i) {
                ItemStack s = inv.getStackInSlot(i);
                if (s != null && s.getDisplayName().replaceAll("§[0-9a-fk-or]", "").contains(name)) {
                    ThelowStorageMod.storageHandler.setPendingStorage(s.getDisplayName());
                    // パケットを直接送信して「掴む」描画をスキップ
                    sendSilentClick(parent, i);
                    return;
                }
            }
        } else {
            String label = targetPage + "ページ目を表示";
            for(int i = 0; i < inv.getSizeInventory(); ++i) {
                ItemStack s = inv.getStackInSlot(i);
                if (s != null && s.getDisplayName().contains(label)) {
                    sendSilentClick(parent, i);
                    return;
                }
            }
        }
    }
    private void sendSilentClick(GuiChest parent, int slotId) {
        if (slotId < 0) return;
        short tid = parent.inventorySlots.getNextTransactionID(this.mc.thePlayer.inventory);
        ItemStack clickedStack = parent.inventorySlots.getSlot(slotId).getStack();

        // windowClickではなく、直接ネットワークハンドラへ送信
        this.mc.getNetHandler().addToSendQueue(new net.minecraft.network.play.client.C0EPacketClickWindow(
                parent.inventorySlots.windowId, slotId, 0, 0, clickedStack, tid
        ));
    }
    // --- 最終安定版: プレイヤーインベントリの描画 ---
    private void drawPlayerInventory(int mouseX, int mouseY, GuiChest parent, int bottomLimit) {
        ContainerChest container = (ContainerChest) parent.inventorySlots;
        int chestSize = container.getLowerChestInventory().getSizeInventory();

        float invScale = getInventoryScale();
        float screenW = this.width / invScale;
        float screenH = this.height / invScale;

        int startX = (int)((screenW - (9 * 18)) / 2);
        int startY = (int)(screenH - (4 * 18 + 4) - 10);
        int hotbarY = startY + 3 * 18 + 4;

        int vmx = (int)(mouseX / invScale);
        int vmy = (int)(mouseY / invScale);

        Slot hoveredSlot = null;

        // --- A. Matrix開始 & スケーリング ---
        GlStateManager.pushMatrix();
        GlStateManager.scale(invScale, invScale, 1.0F);

        // Zレベルの競合を避けるため、全体の基準を少し手前に出す
        GlStateManager.translate(0, 0, 100);

        // 1. グリッド背景の描画 (ライティングは無効にする)
        GlStateManager.disableLighting();
        int gridColor = 0xFF555555;
        this.zLevel = 0; // 背景は一番奥
        drawRect(startX - 2, startY - 2, startX + (9 * 18) + 2, startY + (3 * 18) + 2, gridColor);
        drawRect(startX - 2, hotbarY - 2, startX + (9 * 18) + 2, hotbarY + 18 + 2, gridColor);

        // 2. スロットとアイテムの描画ループ
        for (int i = 0; i < 36; i++) {
            int index = chestSize + i;
            int col = i % 9;
            int row = i / 9;
            int x = startX + col * 18;
            int y = (i < 27) ? (startY + row * 18) : hotbarY;

            Slot slot = container.getSlot(index);

            // スロットの背景（凹み）
            this.zLevel = 1;
            drawRect(x, y, x + 16, y + 16, 0x44000000);

            // アイテムの描画
            if (slot.getHasStack()) {
                // 前の描画の色（灰色など）がアイテムに混ざらないよう白にリセット
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                // アイテム描画用のライティングを有効化
                RenderHelper.enableGUIStandardItemLighting();

                // アイテムを少し手前に出す
                this.mc.getRenderItem().zLevel = 100.0F;
                this.mc.getRenderItem().renderItemAndEffectIntoGUI(slot.getStack(), x, y);
                this.mc.getRenderItem().renderItemOverlays(this.fontRendererObj, slot.getStack(), x, y);
                this.mc.getRenderItem().zLevel = 0.0F;

                RenderHelper.disableStandardItemLighting();
            }

            // ホバー判定
            if (vmx >= x && vmx < x + 18 && vmy >= y && vmy < y + 18) {
                hoveredSlot = slot;
                // ハイライトを一番手前に描画
                GlStateManager.disableLighting();
                GlStateManager.disableDepth(); // ハイライトがアイテムに埋まらないようにする
                drawRect(x, y, x + 16, y + 16, 0x80FFFFFF);
                GlStateManager.enableDepth();
            }
        }

        // 全て終わったらライティングとMatrixを完全にリセット
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();

        // --- B. ツールチップの描画 (Matrixの外、かつ最前面) ---
        if (hoveredSlot != null && hoveredSlot.getHasStack() && this.mc.thePlayer.inventory.getItemStack() == null) {
            // 他のGL状態がツールチップの背景（暗い部分）に悪影響を及ぼさないようリセット
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            // ツールチップ自体のzLevelは内部で制御されるためそのまま呼ぶ
            this.renderToolTip(hoveredSlot.getStack(), mouseX, mouseY);

            GlStateManager.enableLighting();
            GlStateManager.enableDepth();
        }

        // 最後に念のためライティングを戻す
        RenderHelper.enableGUIStandardItemLighting();
    }

    private void drawSlot(Slot slot, int x, int y, int vmx, int vmy) {
        // 各スロットの背景（少し暗くして格子状に見せる）
        drawRect(x, y, x + 16, y + 16, 0x44000000);

        if (slot.getHasStack()) {
            this.mc.getRenderItem().renderItemAndEffectIntoGUI(slot.getStack(), x, y);
            this.mc.getRenderItem().renderItemOverlays(this.fontRendererObj, slot.getStack(), x, y);
        }

        // マウスホバー時の白いハイライトのみ描画
        if (vmx >= x && vmx < x + 18 && vmy >= y && vmy < y + 18) {
            drawRect(x, y, x + 16, y + 16, 0x80FFFFFF);
        }
    }

    /**
     * プレイヤーインベントリ領域のクリックを正確に処理します。
     * 修正点: ContainerChestのスロットID仕様に合わせて計算式を補正しました。
     */
    private void handlePlayerInventoryClick(int mouseX, int mouseY, int mouseButton, GuiChest parent, int bottomLimit) {
        if (this.mc.thePlayer.openContainer == null) return;

        ContainerChest container = (ContainerChest) parent.inventorySlots;
        int chestSize = container.getLowerChestInventory().getSizeInventory();

        float invScale = getInventoryScale();
        float screenW = this.width / invScale;
        float screenH = this.height / invScale;

        // 描画座標 (drawPlayerInventoryと一致させる)
        int startX = (int)((screenW - (9 * 18)) / 2);
        int startY = (int)(screenH - (4 * 18 + 4) - 10);
        int hotbarY = startY + 3 * 18 + 4;

        int vmx = (int)(mouseX / invScale);
        int vmy = (int)(mouseY / invScale);
        int windowId = this.mc.thePlayer.openContainer.windowId;

        // A. メインインベントリ (上3段: Containerスロット番号 chestSize ～ chestSize+26)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = startX + col * 18;
                int y = startY + row * 18;
                if (vmx >= x && vmx < x + 18 && vmy >= y && vmy < y + 18) {
                    int slotIndex = chestSize + col + (row * 9);

                    // --- ここにダブルクリック判定を追加 ---
                    this.performClick(windowId, slotIndex, mouseButton);

                    return; // 元のコード通り、処理後は即座に抜ける
                }
            }
        }

        // B. ホットバー (最下段: Containerスロット番号 chestSize+27 ～ chestSize+35)
        for (int col = 0; col < 9; col++) {
            int x = startX + col * 18;
            if (vmx >= x && vmx < x + 18 && vmy >= hotbarY && vmy < hotbarY + 18) {
                int slotIndex = chestSize + 27 + col;

                // --- ここにダブルクリック判定を追加 ---
                this.performClick(windowId, slotIndex, mouseButton);

                return; // 元のコード通り、処理後は即座に抜ける
            }
        }
    }

    private void performClick(int windowId, int slotIndex, int mouseButton) {
        long currentTime = net.minecraft.client.Minecraft.getSystemTime();
        int mode = 0;

        ItemStack heldStack = this.mc.thePlayer.inventory.getItemStack();

        // 1. Shiftキー
        if (isShiftKeyDown()) {
            mode = 1;
        }
        // 2. 左ダブルクリック
        else if (mouseButton == 0
                && heldStack != null
                && this.lastClickSlotId == slotIndex
                && this.lastClickButton == mouseButton
                && currentTime - this.lastClickTime < 250L) {
            mode = 6;
        }
        // 3. 右クリック
        else if (mouseButton == 1) {
            // ★修正: handleRightClickDrag に任せるため無視
            return;
        }

        // 履歴更新
        this.lastClickSlotId = slotIndex;
        this.lastClickTime = currentTime;
        this.lastClickButton = mouseButton;

        this.mc.playerController.windowClick(windowId, slotIndex, mouseButton, mode, this.mc.thePlayer);
    }

    public void handleRightClickDrag(int mouseX, int mouseY) {
        // 1. Mod GUIが無効なら即終了
        if (!ConfigHandler.isOverlayEnabled) {
            this.lastDragSlotId = -1;
            return;
        }

        // 2. 右クリックチェック
        if (!org.lwjgl.input.Mouse.isButtonDown(1)) {
            if (this.lastDragSlotId != -1) {
                this.lastDragSlotId = -1;
            }
            return;
        }

        // 3. アイテム所持チェック
        ItemStack heldStack = this.mc.thePlayer.inventory.getItemStack();
        if (heldStack == null || heldStack.stackSize <= 0) {
            return;
        }

        if (!(this.mc.currentScreen instanceof GuiChest)) return;
        GuiChest parent = (GuiChest) this.mc.currentScreen;

        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(this.mc);
        int screenWidth = sr.getScaledWidth();
        int screenHeight = sr.getScaledHeight();
        int guiLimit = getGuiHeightLimit();

        // === A. Mod GUI (倉庫側) ===
        if (mouseY <= guiLimit) {
            float totalScale = getTotalScale();
            float fillScale = getFillWidthScale();
            float vHeightAbs = (float)screenHeight / getAbsoluteScaleFactor();
            int searchH = (int)(vHeightAbs * 0.04f);
            float itemsStartY = (vHeightAbs * 0.01f) + (float)searchH + (vHeightAbs * 0.05f);
            float currentY = (itemsStartY / fillScale) - this.scrollY;

            float sw = (float)screenWidth / totalScale;
            float startX = (sw - (float)(currentColumns * BASE_ENTRY_WIDTH)) / 2.0f;

            float smx = (float)mouseX / totalScale;
            float smy = (float)mouseY / totalScale;

            List<Entry<String, ItemStack[]>> entries = StorageCache.getSortedEntries();
            boolean isCurrentEnderChest = isEnderChest(parent);

            for (int i = 0; i < entries.size(); ++i) {
                Entry<String, ItemStack[]> entry = entries.get(i);
                String cleanName = entry.getKey().replaceAll("§[0-9a-fk-or]", "").replaceAll("[\\\\/:*?\"<>|]", "_");
                if (!cleanName.equals(StorageHandler.getActiveStorageName())) continue;

                int rows = (entry.getValue().length <= 27) ? 3 : 6;
                int logicalRows = (isCurrentEnderChest) ? 3 : rows;
                float xPos = startX + (float)(i % currentColumns) * BASE_ENTRY_WIDTH;
                float yPos = currentY + (float)(i / currentColumns) * BASE_ENTRY_HEIGHT;

                if (smx >= xPos && smx <= xPos + 162 && smy >= yPos && smy <= yPos + (logicalRows * 18)) {
                    int col = (int)((smx - xPos) / 18);
                    int row = (int)((smy - yPos) / 18);
                    int slotIndex = col + (row * 9);

                    if (slotIndex >= 0 && slotIndex < entry.getValue().length) {
                        if (slotIndex == this.lastDragSlotId) return;

                        // 空きスロットチェック
                        if (entry.getValue()[slotIndex] == null) {
                            // 1. サーバーへ送信
                            this.mc.playerController.windowClick(parent.inventorySlots.windowId, slotIndex, 1, 0, this.mc.thePlayer);

                            // 2. クライアントキャッシュの即時更新（重複配置対策）
                            ItemStack dummyStack = heldStack.copy();
                            dummyStack.stackSize = 1;
                            entry.getValue()[slotIndex] = dummyStack;
                            ThelowStorageMod.storageHandler.triggerCacheUpdate();

                            // 3. ★重要★ 手持ちアイテムの即時減算（ゴースト対策）
                            // サーバーからの同期を待たず、ここで強制的に1個減らす
                            heldStack.stackSize--;
                            if (heldStack.stackSize <= 0) {
                                this.mc.thePlayer.inventory.setItemStack(null);
                            }

                            this.lastDragSlotId = slotIndex;
                        }
                    }
                    return;
                }
            }
        }
        // === B. プレイヤーインベントリ側 ===
        else {
            if (parent.inventorySlots instanceof ContainerChest) {
                ContainerChest container = (ContainerChest) parent.inventorySlots;
                int chestSize = container.getLowerChestInventory().getSizeInventory();
                float invScale = getInventoryScale();
                float screenW = screenWidth / invScale;
                float screenH = screenHeight / invScale;
                int startX = (int)((screenW - (9 * 18)) / 2);
                int startY = (int)(screenH - (4 * 18 + 4) - 10);
                int hotbarY = startY + 3 * 18 + 4;
                int vmx = (int)(mouseX / invScale);
                int vmy = (int)(mouseY / invScale);

                int targetSlotIndex = -1;
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 9; col++) {
                        int x = startX + col * 18;
                        int y = startY + row * 18;
                        if (vmx >= x && vmx < x + 18 && vmy >= y && vmy < y + 18) {
                            targetSlotIndex = chestSize + col + (row * 9);
                            break;
                        }
                    }
                }
                if (targetSlotIndex == -1) {
                    for (int col = 0; col < 9; col++) {
                        int x = startX + col * 18;
                        if (vmx >= x && vmx < x + 18 && vmy >= hotbarY && vmy < hotbarY + 18) {
                            targetSlotIndex = chestSize + 27 + col;
                            break;
                        }
                    }
                }

                if (targetSlotIndex != -1) {
                    if (targetSlotIndex == this.lastDragSlotId) return;

                    if (!container.getSlot(targetSlotIndex).getHasStack()) {
                        this.mc.playerController.windowClick(container.windowId, targetSlotIndex, 1, 0, this.mc.thePlayer);

                        // ★重要★ 手持ちアイテムの即時減算（ゴースト対策）
                        heldStack.stackSize--;
                        if (heldStack.stackSize <= 0) {
                            this.mc.thePlayer.inventory.setItemStack(null);
                        }

                        this.lastDragSlotId = targetSlotIndex;
                    }
                }
            }
        }
    }
}