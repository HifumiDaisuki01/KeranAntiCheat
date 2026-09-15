package com.keran.kac.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端指纹数据(每个玩家一份)。
 */
public class ClientData {

    public String name;
    public long lastReport;
    public long lastHeartbeat;
    public String fingerprint = "";
    public int modCount;
    public List<String> blacklistHits = new ArrayList<>();
    public List<String> blacklistSources = new ArrayList<>();
    public List<String> suspicious = new ArrayList<>();
    public List<String> jvmAgents = new ArrayList<>();
    public List<String> suspiciousPacks = new ArrayList<>();
    public int heartbeatTimeoutWarned;
    /** 连续收到非法指纹上报的次数(修复 KAC-02) */
    public int invalidReports;

    /** 详细清单(连入自动上报): mods + mod_files + pack_files */
    public JsonObject detailReport;
    /** MD5 清单(连入自动上报): mod_files + pack_files 带 md5 */
    public JsonObject filesReport;
    public long detailTime;
    public long filesTime;

    /** mods 目录整体 MD5 (自动上报) */
    public String modsDirMd5 = "";
    /** 材质包目录整体 MD5 (自动上报) */
    public String packsDirMd5 = "";
    /** mods 目录文件数 (自动上报) */
    public int modFileCount;
    /** 材质包目录文件数 (自动上报) */
    public int packFileCount;

    public ClientData(String name) {
        this.name = name;
    }

    public boolean hasReported() {
        return lastReport > 0;
    }
}
