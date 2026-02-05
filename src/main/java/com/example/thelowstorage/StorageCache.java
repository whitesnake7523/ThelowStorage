package com.example.thelowstorage;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.apache.commons.io.FileUtils;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StorageCache {
    private static final Map<String, ItemStack[]> CACHE = new LinkedHashMap<>();

    public static List<Map.Entry<String, ItemStack[]>> getSortedEntries() {
        synchronized (CACHE) {
            List<Map.Entry<String, ItemStack[]>> list = new ArrayList<>(CACHE.entrySet());
            list.sort((o1, o2) -> Integer.compare(getPriority(o1.getKey()), getPriority(o2.getKey())));
            return list;
        }
    }

    public static void updateSingleCache(String name, ItemStack[] items) {
        synchronized (CACHE) {
            CACHE.put(name, items);
        }
    }

    public static void loadAllFromDisk() {
        File[] files = ThelowStorageMod.configDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;
        synchronized (CACHE) {
            CACHE.clear();
            for (File file : files) {
                try {
                    String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                    NBTTagCompound root = JsonToNBT.getTagFromJson(content);
                    CACHE.put(file.getName().replace(".json", ""), convertNBTToItems(root));
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }

    public static int getPriority(String name) {
        String cleanName = name.replaceAll("§[0-9a-fk-or]", "").trim();
        if (cleanName.contains("エンダーチェスト")) return 0;
        if (cleanName.contains("馬用チェスト")) return 1;
        if (cleanName.contains("鍵用チェスト")) return 2;
        if (cleanName.contains("My Collection")) return 3;
        if (cleanName.contains("Senior Chest")) {
            Matcher m = Pattern.compile("Senior Chest ([A-E])").matcher(cleanName);
            if (m.find()) return 2000 + (m.group(1).charAt(0) - 'A');
        }
        Matcher m = Pattern.compile("Type([A-Z])(\\d*)").matcher(cleanName);
        if (m.find()) {
            char alpha = m.group(1).charAt(0);
            int gen = m.group(2).isEmpty() ? 1 : Integer.parseInt(m.group(2));
            if (gen == 1) {
                if (alpha <= 'Q') return 1000 + (alpha - 'A');
                return 3000 + (alpha - 'R');
            }
            return (gen + 2) * 1000 + (alpha - 'A');
        }
        return 9999;
    }

    public static int getPageFromPriority(int p) {
        if (p < 3000) return 1;
        if (p >= 3000 && p <= 4014) return 2;
        if (p >= 4015 && p <= 5011) return 3;
        return 4;
    }

    private static ItemStack[] convertNBTToItems(NBTTagCompound root) {
        NBTTagList list = root.getTagList("Items", 10);
        int maxSlotIndex = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            maxSlotIndex = Math.max(maxSlotIndex, list.getCompoundTagAt(i).getInteger("Slot"));
        }
        // 最大スロットが26以下ならエンダーチェスト(27)、それ以上なら通常(54)として扱う
        int size = (maxSlotIndex < 27) ? 27 : 54;
        ItemStack[] items = new ItemStack[size];

        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getInteger("Slot");
            if (slot >= 0 && slot < size && tag.hasKey("id")) {
                items[slot] = ItemStack.loadItemStackFromNBT(tag);
            }
        }
        return items;
    }
}