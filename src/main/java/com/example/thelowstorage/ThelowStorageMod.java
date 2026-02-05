package com.example.thelowstorage;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import java.io.File;

// guiFactoryのパスをプロジェクトのパッケージ名に合わせて指定
@Mod(modid = "ThelowStorage", version = "0.3", guiFactory = "com.example.thelowstorage.ModGuiFactory")
public class ThelowStorageMod {
    public static File configDir;
    public static StorageHandler storageHandler;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigHandler.init(event.getSuggestedConfigurationFile());

        configDir = new File(event.getModConfigurationDirectory(), "ThelowStorage");
        if (!configDir.exists()) configDir.mkdirs();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        storageHandler = new StorageHandler();
        MinecraftForge.EVENT_BUS.register(storageHandler);

        FMLCommonHandler.instance().bus().register(new ConfigHandler());

        StorageCache.loadAllFromDisk();
    }
}