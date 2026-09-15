package com.keran.kac.check.world;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * 聊天刷屏检测(Chat)：每秒消息数超限则取消并禁言。
 */
public class ChatCheck extends Check {

    public ChatCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.CHAT);
    }

    @Override
    public void onChat(AsyncPlayerChatEvent e, PlayerData data) {
        long now = System.currentTimeMillis();
        if (data.chatWindowStart == 0) {
            data.chatWindowStart = now;
        }
        data.chatsThisSecond++;

        if (now - data.chatWindowStart >= 1000) {
            int chats = data.chatsThisSecond;
            data.chatsThisSecond = 0;
            data.chatWindowStart = now;
            int maxChats = plugin.getConfigManager().getCheckInt(type, "max-chats-per-second", 4);
            if (chats > maxChats) {
                data.muteUntil = now + 10_000L; // 禁言 10 秒
                flag(data, "每秒聊天 " + chats + " 条, 已禁言 10 秒", 1.0);
                e.setCancelled(true);
            }
        }
    }

    @Override
    public long eventMask() {
        return EV_CHAT;
    }
}
