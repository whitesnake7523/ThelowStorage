package com.example.thelowstorage;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import java.util.ArrayList;
import java.util.List;

public class ModConfigGui extends GuiConfig {
    public ModConfigGui(GuiScreen parent) {
        super(
                parent,
                // 全カテゴリを表示するためにリストを渡す
                getAllElements(),
                "ThelowStorage", // MODIDを直接書くか参照する
                false,
                false,
                "ThelowStorage 設定"
        );
    }

    private static List<IConfigElement> getAllElements() {
        List<IConfigElement> list = new ArrayList<>();
        if (ConfigHandler.config != null) {
            for (String categoryName : ConfigHandler.config.getCategoryNames()) {
                list.addAll(new ConfigElement(ConfigHandler.config.getCategory(categoryName)).getChildElements());
            }
        }
        return list;
    }
}