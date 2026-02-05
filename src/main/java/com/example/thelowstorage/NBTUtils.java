package com.example.thelowstorage;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class NBTUtils {

    /**
     * インベントリの内容をNBT形式で保存します。
     * エンダーチェスト(27スロット)の場合は、超過分を空として扱い、一貫したキャッシュを作成します。
     */
    public static void saveInventoryToNBT(IInventory inv, String fileName) {
        if (fileName == null) return;

        try {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList itemList = new NBTTagList();

            // 内部のデータ整合性を保つため、表示・管理用の最大枠である54スロットの配列を用意
            ItemStack[] cacheItems = new ItemStack[54];

            // 実際のインベントリサイズ（エンダーチェストなら27、大チェストなら54など）
            int invSize = inv.getSizeInventory();

            for (int i = 0; i < 54; i++) {
                NBTTagCompound slotTag = new NBTTagCompound();
                slotTag.setInteger("Slot", i);

                // インベントリの範囲内のスロットのみ処理
                if (i < invSize) {
                    ItemStack stack = inv.getStackInSlot(i);
                    if (stack != null) {
                        String displayName = stack.getDisplayName();
                        // 不要なボタンアイテムを除外して保存
                        if (!displayName.equals("ここにはアイテムを置けません。") &&
                                !displayName.equals("一覧画面に戻る")) {

                            stack.writeToNBT(slotTag);
                            cacheItems[i] = stack;
                        }
                    }
                } else {
                    // エンダーチェスト等で27スロットを超える分（28〜54）は、
                    // stackがnullの状態でslotTagが作成され、空きスロットとして扱われる
                }

                itemList.appendTag(slotTag);
            }

            root.setTag("Items", itemList);
            File file = new File(ThelowStorageMod.configDir, fileName + ".json");

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(root.toString());
            }

            // メモリ上のキャッシュを即時更新
            StorageCache.updateSingleCache(fileName, cacheItems);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}