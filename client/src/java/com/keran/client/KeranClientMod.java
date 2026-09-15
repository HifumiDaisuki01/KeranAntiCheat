package com.keran.client;

import com.keran.client.command.ClientCommand;
import com.keran.client.config.ClientConfig;
import com.keran.client.net.ReportSender;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Keran Client - 反作弊防注入客户端检测 Mod (Fabric 1.16.5)
 * 主入口：加载配置、注册命令、注册进服事件、启动心跳。
 */
public class KeranClientMod implements ClientModInitializer {

    public static final String MOD_ID = "keranclient";
    public static final String MOD_VERSION = "1.0.0";

    private static ClientConfig config;
    private static boolean enabled = true;

    @Override
    public void onInitializeClient() {
        config = ClientConfig.load();

        // 进服后延迟 3 秒发送详细报告, 之后每 30 秒心跳
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!enabled) {
                return;
            }
            if (!config.isAllowedServer(handler.getConnection().getAddress().toString())) {
                // 不在白名单的服务器: 只发心跳, 不发详细指纹(隐私保护)
                ReportSender.startHeartbeat(null);
                return;
            }
            MinecraftClient.getInstance().execute(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }
                ReportSender.sendReport();
            });
            ReportSender.startHeartbeat(config);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ReportSender.stopHeartbeat());

        // 接收服务端查询指令(详细清单 / MD5)
        com.keran.client.net.QueryReceiver.register();

        ClientCommand.register();

        com.keran.client.KeranClientMod.LOGGER.info("KeranClient v" + MOD_VERSION + " 已加载 (反作弊防注入客户端检测)");
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
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean e) {
        enabled = e;
    }
}
