package com.keran.client;

import com.keran.client.command.ClientCommand;
import com.keran.client.config.ClientConfig;
import com.keran.client.net.ReportSender;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Keran Client - 反作弊防注入客户端检测 Mod (Fabric 1.20.1)
 * 主入口：加载配置、注册命令、注册进服事件、启动心跳。
 */
public class KeranClientMod implements ClientModInitializer {

    public static final String MOD_ID = "keranclient";
    public static final String MOD_VERSION = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer(MOD_ID).orElseThrow().getMetadata().getVersion().getFriendlyString();

    private static ClientConfig config;

    @Override
    public void onInitializeClient() {
        config = ClientConfig.load();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                ReportSender.startHeartbeat(config));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ReportSender.stopHeartbeat());

        // 接收服务端查询指令(详细清单 / MD5)
        com.keran.client.net.QueryReceiver.register();

        ClientCommand.register();

        com.keran.client.KeranClientMod.LOGGER.info("KeranClient v" + MOD_VERSION + " 已加载 (反作弊防注入客户端检测, 强制开启)");
    }

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("KeranClient");

    public static org.apache.logging.log4j.Logger getLogger() {
        return LOGGER;
    }

    public static ClientConfig getConfig() {
        return config;
    }

    public static void reloadConfig() {
        config = ClientConfig.load();
        ReportSender.reloadPolicy(config);
    }
}
