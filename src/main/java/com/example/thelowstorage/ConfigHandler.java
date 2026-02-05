package com.example.thelowstorage;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.io.File;

public class ConfigHandler {
    public static Configuration config;

    // 設定項目
    public static boolean useAbsoluteScaling = true;
    public static boolean isOverlayEnabled = true; // GUIの表示フラグ
    public static int currentColumns = 3;          // 列数の保存

    public static void init(File configFile) {
        config = new Configuration(configFile);
        config.load();
        syncConfig();
    }

    public static void syncConfig() {
        // レイアウト設定
        useAbsoluteScaling = config.getBoolean("useAbsoluteScaling", "Layout", true, "絶対スケーリングの使用");
        currentColumns = config.getInt("currentColumns", "Layout", 3, 1, 10, "表示する列数");

        // GUI状態
        isOverlayEnabled = config.getBoolean("isOverlayEnabled", "General", false, "ストレージオーバーレイを表示するかどうか");

        if (config.hasChanged()) {
            config.save();
        }
    }

    // 列数などがGUI操作で変わった時に、外部から保存を呼び出すためのメソッド
    public static void setColumns(int columns) {
        currentColumns = columns;
        config.get("Layout", "currentColumns", 3).set(columns);
        config.save();
    }

    public static void setOverlayEnabled(boolean enabled) {
        isOverlayEnabled = enabled;
        config.get("General", "isOverlayEnabled", true).set(enabled);
        config.save();
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.modID.equals("ThelowStorage")) {
            syncConfig();
        }
    }
}