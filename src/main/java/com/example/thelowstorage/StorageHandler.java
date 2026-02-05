package com.example.thelowstorage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL11; // 追加

public class StorageHandler {
    private final StorageGUI overlay = new StorageGUI();
    private String lastClickedItemName = null;
    private static String activeStorageName = null; // 現在実体が開いているストレージ名
    private int waitTicks = -1;
    private String lastActiveStorageName = null; // 遷移中の描画維持用
    private boolean wasGuiOpen = false;

    public static String getActiveStorageName() {
        return activeStorageName;
    }
    public static void resetActiveStorage() {
        activeStorageName = null;
    }
    private boolean isTargetContainer(IInventory inv) {
        String title = inv.getDisplayName().getUnformattedText();

        // 1. 倉庫ページ一覧画面（これは常にModの対象）
        if (title.contains("倉庫 -") && title.contains("ページ目")) return true;

        // 2. 実体（名前がバニラ、または「倉庫」と付くもの）
        boolean isGenericChest = title.equals("Chest") || title.equals("エンダーチェスト");

        if (isGenericChest) {
            // 【重要】ModがONであっても、現在どのストレージを開こうとしているか(lastClicked)
            // または既に開いているか(activeStorage)の情報がある時だけ Mod GUI を表示する。
            // これにより、ただのチェストを開いた時にMod GUIが出るのを防ぎます。
            return activeStorageName != null || lastClickedItemName != null;
        }
        return false;
    }
    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.gui instanceof GuiChest && !(event.gui instanceof StorageGUI)&& ConfigHandler.isOverlayEnabled) {
            GuiChest guiChest = (GuiChest) event.gui;
            IInventory inv = ((ContainerChest) guiChest.inventorySlots).getLowerChestInventory();

            overlay.handleRightClickDrag(event.mouseX, event.mouseY);

            if (isTargetContainer(inv) && StorageGUI.isOverlayEnabled) {
                // --- 追加: シザーテストを解除 ---
                // これをしないとMod GUI自体も描画されなくなってしまうため必須
                GL11.glDisable(GL11.GL_SCISSOR_TEST);

                // バニラの描画が終わった（かつ上部がカットされた）後にMod GUIを重ねて描画
                overlay.renderFullGUI(event.mouseX, event.mouseY, guiChest);
            }
        }
    }


// --- 既存の変数定義から「スロット隠蔽用」のマップや変数を削除 ---
    // private final Map<Slot, int[]> originalSlotPositions ... (削除)
    // private boolean slotsHidden ... (削除)
    // リフレクション関連 ... (削除)

    // --- 描画イベント: バニラ描画を完全キャンセルし、Mod GUIで全画面描画 ---
    @SubscribeEvent
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (event.gui instanceof GuiChest && !(event.gui instanceof StorageGUI)) {
            GuiChest guiChest = (GuiChest) event.gui;
            IInventory inv = ((ContainerChest) guiChest.inventorySlots).getLowerChestInventory();

            if (isTargetContainer(inv)) {
                if (StorageGUI.isOverlayEnabled) {
                    // Mod GUIがONならバニラをキャンセルして自前で描画
                    event.setCanceled(true);
                    overlay.setWorldAndResolution(Minecraft.getMinecraft(), guiChest.width, guiChest.height);
                    overlay.renderFullGUI(event.mouseX, event.mouseY, guiChest);
                } else {
                    // Mod GUIがOFFでも、ボタンだけを重ねて描画するために renderFullGUI を呼ぶ
                    // ただし event.setCanceled(true) はしない（バニラを描画させる）
                    overlay.setWorldAndResolution(Minecraft.getMinecraft(), guiChest.width, guiChest.height);
                    overlay.renderFullGUI(event.mouseX, event.mouseY, guiChest);
                }
            }
        }
    }

// --- マウス入力: バニラ処理の前（Pre）にModの判定を割り込ませる ---
// StorageHandler.java の onMouseInput メソッド内

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (event.gui instanceof GuiChest) {
            GuiChest guiChest = (GuiChest) event.gui;
            IInventory inv = ((ContainerChest) guiChest.inventorySlots).getLowerChestInventory();

            String title = inv.getDisplayName().getUnformattedText();

            // isPressedの判定を削除し、押下・離上の両方で実行されるように変更
            if (title.contains("倉庫 -") && title.contains("ページ目")) {
                Slot slot = guiChest.getSlotUnderMouse();
                if (slot != null && slot.getHasStack()) {
                    String name = slot.getStack().getDisplayName();
                    if (name.contains("Type") || name.contains("Collection") || name.contains("チェスト")||name.contains("Senior")) {
                        this.setPendingStorage(name);
                    }
                }
            }

            if (isTargetContainer(inv)) {
                Minecraft mc = Minecraft.getMinecraft();
                ScaledResolution sr = new ScaledResolution(mc);
                int button = Mouse.getEventButton();
                int mx = Mouse.getEventX() * sr.getScaledWidth() / mc.displayWidth;
                int my = sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / mc.displayHeight - 1;

                overlay.setWorldAndResolution(mc, guiChest.width, guiChest.height);

                // Mod側の処理結果を受け取る
                boolean handled = overlay.handleMouseInputs(mx, my, button, guiChest);

                // 【重要】
                // handled が true なら Mod が入力を食べたのでキャンセル。
                // handled が false ならバニラに処理を任せる。
                if (handled) {
                    event.setCanceled(true);
                }
            }


        }
    }
    /**
     * キーボード入力イベントの制御
     * バニラ準拠の「押下時のみ処理」に統一することで、IMEとBackspaceの問題を同時に解決します。
     */
// StorageHandler.java

// StorageHandler.java

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (event.gui instanceof GuiChest) {
            GuiChest guiChest = (GuiChest) event.gui;
            IInventory inv = ((ContainerChest) guiChest.inventorySlots).getLowerChestInventory();

            if (isTargetContainer(inv) && StorageGUI.isOverlayEnabled) {

                // キーが押された瞬間だけ処理
                if (!Keyboard.getEventKeyState()) return;

                char typedChar = Keyboard.getEventCharacter();
                int keyCode = Keyboard.getEventKey();

                if (keyCode == Keyboard.KEY_ESCAPE) {
                    return;
                }

                // 画面サイズ情報の同期のみ行う
                overlay.setWorldAndResolution(Minecraft.getMinecraft(), guiChest.width, guiChest.height);

                // ★修正: mouseX, mouseY を渡さず、StorageGUI内部で計算させる
                if (overlay.keyTyped(typedChar, keyCode, guiChest)) {
                    event.setCanceled(true);
                }
            }
        }
    }

// StorageHandler.java 内の onTick メソッド

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();

        // 検索バーのカーソル点滅などの更新
        overlay.updateSearchField();

        // 現在 GuiChest (バニラのチェスト画面) が開いているか確認
        boolean isGuiOpen = mc.currentScreen instanceof GuiChest;

        // --- 1. GUIの状態変化検知 (IMEリセットやフォーカス制御) ---
        if (!wasGuiOpen && isGuiOpen) {
            overlay.onGuiOpened(); //
        } else if (wasGuiOpen && !isGuiOpen) {
            Keyboard.enableRepeatEvents(false); //
            overlay.onGuiClosed(); //
        }
        wasGuiOpen = isGuiOpen;

        // --- 2. メインロジック: ストレージ名の特定と保存処理 ---
        if (isGuiOpen) {
            GuiChest guiChest = (GuiChest) mc.currentScreen;
            IInventory inv = ((ContainerChest) guiChest.inventorySlots).getLowerChestInventory();
            String title = inv.getDisplayName().getUnformattedText();

            // A. 倉庫ページ一覧画面を開いている場合
            if (title.contains("倉庫 -") && title.contains("ページ目")) {
                // ページ一覧を開いている時は、個別のストレージ実体は開いていない状態にする
                if (activeStorageName != null) this.lastActiveStorageName = activeStorageName;
                activeStorageName = null;
            }
            // B. 「Chest」や「エンダーチェスト」などの実体を開いている場合
            else {
                // 前の画面でクリックした名前があるなら、それを現在の有効なストレージ名として確定
                if (activeStorageName == null && lastClickedItemName != null) {
                    activeStorageName = lastClickedItemName;
                    //Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("ActiveStorageNameが更新されました:"+activeStorageName));
                    this.lastActiveStorageName = activeStorageName;
                    // 注意: ここで lastClickedItemName = null にすると判定が途切れるため、保存成功時まで保持します
                }

                // 有効なストレージ名が決まっており、保存待機カウント(waitTicks)が設定されている場合
                if (activeStorageName != null && waitTicks >= 0) {
                    // サーバーから中身が送られてくるのを待つため、カウントダウン
                    if (waitTicks == 0) {
                        // カウントが0になったらディスクへ保存
                        NBTUtils.saveInventoryToNBT(inv, activeStorageName);

                        // 保存が完了したので、クリック情報の予約をクリア
                        lastClickedItemName = null;
                        waitTicks = -1;
                    } else {
                        waitTicks--;
                    }
                }
            }
        }
        // --- 3. GUIが閉じている時のリセット処理 ---
        else if (mc.currentScreen == null) {
            // 次のクリック待ちでない（lastClickedItemNameが空）なら、完全にリセット
            if (lastClickedItemName == null) {
                activeStorageName = null;
                this.lastActiveStorageName = null;
                waitTicks = -1;
            }
        } else {
            // チェスト以外の画面（設定画面など）が開いた場合もリセット
            activeStorageName = null;
            lastClickedItemName = null;
            this.lastActiveStorageName = null;
            waitTicks = -1;
        }
    }

    public void setPendingStorage(String rawName) {
        if (rawName == null) return;
        this.lastClickedItemName = rawName.replaceAll("§[0-9a-fk-or]", "").replaceAll("[\\\\/:*?\"<>|]", "_");
        activeStorageName = null;
        this.waitTicks = 20;
        //Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("lastClickedItemNameが更新されました:"+this.lastClickedItemName));
    }

    // --- 新規追加: キャッシュ更新トリガー ---
    public void triggerCacheUpdate() {
        if (activeStorageName != null) {
            // カウントダウンをリセットし、数ティック後に保存を実行させる
            this.waitTicks = 5;
        }
    }

    @SubscribeEvent
    public void onMouseClickPost(GuiScreenEvent.MouseInputEvent.Post event) {
        if (event.gui instanceof GuiChest) {
            GuiChest guiChest = (GuiChest) event.gui;
            IInventory inv = ((ContainerChest) guiChest.inventorySlots).getLowerChestInventory();
            if ((inv.getDisplayName().getUnformattedText().equals("Chest")||(inv.getDisplayName().getUnformattedText().equals("エンダーチェスト")) && activeStorageName != null)) {
                if (Mouse.getEventButton() != -1 && Mouse.getEventButtonState()) {
                    NBTUtils.saveInventoryToNBT(inv, activeStorageName);
                }
            }
        }
    }

    @SubscribeEvent
    public void onGuiClose(GuiOpenEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiChest && event.gui == null) {
            GuiChest guiChest = (GuiChest) mc.currentScreen;
            IInventory inv = ((ContainerChest) guiChest.inventorySlots).getLowerChestInventory();
            if ((inv.getDisplayName().getUnformattedText().equals("Chest")||(inv.getDisplayName().getUnformattedText().equals("エンダーチェスト")) && activeStorageName != null)) {
                NBTUtils.saveInventoryToNBT(inv, activeStorageName);
                activeStorageName = null;
                lastClickedItemName=null;
            }
        }
    }


}