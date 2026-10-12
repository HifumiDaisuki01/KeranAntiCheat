package com.keran.kac;

import com.keran.kac.bridge.MessageCodec;
import com.keran.kac.data.RecordWriter;
import com.keran.kac.data.PlayerData;
import com.keran.kac.check.CheckType;
import com.keran.kac.util.CombatGeometry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class RegressionTest {
    @TempDir Path temp;
    @Test void legacyOptionalPolicySurvivesDefaultMerge() {
        YamlConfiguration legacy = new YamlConfiguration(); legacy.set("join.enabled", false);
        legacy.set("checks.aim.max-instant-rotation", 250);
        com.keran.kac.config.ConfigManager.migrate(legacy);
        YamlConfiguration defaults = new YamlConfiguration(); defaults.set("client.strict-mode", true);
        legacy.setDefaults(defaults); legacy.options().copyDefaults(true);
        assertFalse(legacy.getBoolean("client.strict-mode"));
        assertEquals(140, legacy.getDouble("checks.aim.max-instant-rotation"));
        assertEquals(4, legacy.getInt("config-version"));
    }
    @Test void explicitNewPolicyOverridesLegacyKeys() {
        YamlConfiguration config = new YamlConfiguration(); config.set("client.strict-mode", true);
        config.set("join.enabled", false); com.keran.kac.config.ConfigManager.migrate(config);
        assertTrue(config.getBoolean("client.strict-mode"));
    }
    @Test void strictPolicyRequiresReportAndOptionalModeAllowsVanilla() {
        assertFalse(com.keran.kac.bridge.ClientPolicy.canPlay(true, false, false));
        assertTrue(com.keran.kac.bridge.ClientPolicy.canPlay(true, false, true));
        assertTrue(com.keran.kac.bridge.ClientPolicy.canPlay(false, false, false));
        assertTrue(com.keran.kac.bridge.ClientPolicy.canPlay(true, true, false));
    }
    @Test void syntheticDamageDoesNotConsumeUnrelatedAttack() {
        PlayerData d = new PlayerData(UUID.randomUUID(), "test"); UUID target = UUID.randomUUID();
        assertFalse(d.consumeAttackRequest(target, 10));
        d.recordAttackRequest(target, 10);
        assertFalse(d.consumeAttackRequest(UUID.randomUUID(), 10));
        assertTrue(d.consumeAttackRequest(target, 10));
        assertFalse(d.consumeAttackRequest(target, 10));
    }
    @Test void oldAttackCannotAuthorizeLaterPluginDamage() {
        PlayerData d = new PlayerData(UUID.randomUUID(), "test"); UUID target = UUID.randomUUID();
        d.recordAttackRequest(target, 10); assertFalse(d.consumeAttackRequest(target, 11));
    }
    @Test void fabricStringRoundTrip() {
        for (String value : new String[]{"", "challenge:abc", "中文", "x".repeat(1000)})
            assertEquals(value, MessageCodec.decode(MessageCodec.encode(value)));
    }
    @Test void invalidFramesRejected() {
        assertNull(MessageCodec.decode(new byte[]{(byte)128}));
        assertNull(MessageCodec.decode(new byte[]{2, 65}));
        assertNull(MessageCodec.decode(new byte[]{1, 65, 66}));
        assertNull(MessageCodec.decode(new byte[]{(byte)255,(byte)255,(byte)255,(byte)255,127}));
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.encode("x".repeat(30001)));
    }
    @Test void depthGuardIgnoresQuotedBraces() {
        assertTrue(MessageCodec.reasonableDepth("{\"x\":\"[[[\\\"\"}"));
        assertFalse(MessageCodec.reasonableDepth("[".repeat(9) + "]".repeat(9)));
        assertFalse(MessageCodec.reasonableDepth("{\"x\":1"));
    }
    @Test void reachUsesNearestBoxPoint() {
        BoundingBox box = new BoundingBox(3,0,-.3,3.6,1.8,.3);
        assertEquals(3, CombatGeometry.distanceToBox(new Vector(0,1.6,0), box), 1e-9);
        assertEquals(0, CombatGeometry.distanceToBox(new Vector(3.2,1.6,0), box), 1e-9);
    }
    @Test void rayRejectsTargetBehindPlayer() {
        BoundingBox box = new BoundingBox(-4,0,-.3,-3,1.8,.3);
        assertNull(box.rayTrace(new Vector(0,1.6,0), new Vector(1,0,0), 10));
        assertNotNull(box.rayTrace(new Vector(0,1.6,0), new Vector(-1,0,0), 10));
    }
    @Test void recordWriterPreservesWriteOrderAndFinishesOnClose() throws Exception {
        Path target = temp.resolve("data/vl.yml");
        RecordWriter writer = new RecordWriter(target, Logger.getAnonymousLogger());
        writer.submit("first"); writer.submit("last"); writer.close();
        assertEquals("last", Files.readString(target));
        try (var paths = Files.list(target.getParent())) { assertEquals(1, paths.count()); }
    }
    @Test void longTermStateSurvivesWhileTransientStateDoesNot() {
        UUID id = UUID.randomUUID();
        PlayerData original = new PlayerData(id,"test");
        original.addVl(CheckType.REACH, 21); original.addPunishment(CheckType.REACH);
        original.addFlag(CheckType.REACH); original.expectedKbSpeed = .8;
        YamlConfiguration yaml = new YamlConfiguration(); original.serialize(yaml);
        PlayerData restored = new PlayerData(id,"test"); restored.deserialize(yaml);
        assertEquals(21, restored.getVl(CheckType.REACH));
        assertEquals(1, restored.getPunishments(CheckType.REACH));
        assertEquals(1, restored.getFlags(CheckType.REACH)); assertEquals(0, restored.expectedKbSpeed);
    }
}
