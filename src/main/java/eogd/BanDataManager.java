package eogd;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class BanDataManager {

    private final TimedBanPlugin plugin;
    private final File bansFile;
    private FileConfiguration bansConfig;
    private final Map<UUID, CustomBanData> activeBans = new ConcurrentHashMap<>();

    public BanDataManager(TimedBanPlugin plugin) {
        this.plugin = plugin;
        this.bansFile = new File(plugin.getPluginDataFolder(), "bans_data.yml");
        if (!bansFile.exists()) {
            try {
                bansFile.getParentFile().mkdirs();
                bansFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "无法创建 bans_data.yml 文件!", e);
            }
        }
        this.bansConfig = YamlConfiguration.loadConfiguration(bansFile);
    }

    public void loadBans() {
        activeBans.clear();
        ConfigurationSection bansSection = bansConfig.getConfigurationSection("bans");
        if (bansSection == null) {
            return;
        }

        boolean modified = false;
        for (String uuidString : bansSection.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidString);
            long expiry = bansSection.getLong(uuidString + ".expiry", 0);

            if (expiry > System.currentTimeMillis()) {
                String reason = bansSection.getString(uuidString + ".reason", "N/A");
                String banner = bansSection.getString(uuidString + ".banner", "Console");
                String durationStr = bansSection.getString(uuidString + ".durationString", "N/A");
                long banTime = bansSection.getLong(uuidString + ".banTime", System.currentTimeMillis());
                int loginAttempts = bansSection.getInt(uuidString + ".loginAttempts", 0);
                activeBans.put(uuid, new CustomBanData(uuid, expiry, reason, banner, durationStr, banTime, loginAttempts));
            } else {
                bansSection.set(uuidString, null);
                modified = true;
                plugin.getLogger().info("已从 bans_data.yml 移除过期的封禁记录: " + uuidString);
            }
        }
        if (modified) {
            saveBansFile();
        }
        plugin.getLogger().info("已加载 " + activeBans.size() + " 条有效的自定义封禁记录。");
    }

    public void saveBans() {
        ConfigurationSection bansSection = bansConfig.createSection("bans");
        for (Map.Entry<UUID, CustomBanData> entry : activeBans.entrySet()) {
            CustomBanData data = entry.getValue();
            if (data.getExpiryTimestamp() > System.currentTimeMillis()) {
                String uuidString = entry.getKey().toString();
                bansSection.set(uuidString + ".expiry", data.getExpiryTimestamp());
                bansSection.set(uuidString + ".reason", data.getOriginalReason());
                bansSection.set(uuidString + ".banner", data.getBannerName());
                bansSection.set(uuidString + ".durationString", data.getTotalDurationString());
                bansSection.set(uuidString + ".banTime", data.getBanTime());
                bansSection.set(uuidString + ".loginAttempts", data.getLoginAttempts());
            }
        }
        saveBansFile();
    }

    private void saveBansFile() {
        try {
            bansConfig.save(bansFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存 bans_data.yml 文件!", e);
        }
    }

    public void addBan(UUID playerUUID, long expiryTimestamp, String reason, String bannerName, String totalDurationString) {
        CustomBanData banData = new CustomBanData(playerUUID, expiryTimestamp, reason, bannerName, totalDurationString, System.currentTimeMillis(), 0);
        activeBans.put(playerUUID, banData);

        String uuidString = playerUUID.toString();
        bansConfig.set("bans." + uuidString + ".expiry", expiryTimestamp);
        bansConfig.set("bans." + uuidString + ".reason", reason);
        bansConfig.set("bans." + uuidString + ".banner", bannerName);
        bansConfig.set("bans." + uuidString + ".durationString", totalDurationString);
        bansConfig.set("bans." + uuidString + ".banTime", banData.getBanTime());
        bansConfig.set("bans." + uuidString + ".loginAttempts", 0);
        saveBansFile();
    }

    public CustomBanData getBan(UUID playerUUID) {
        CustomBanData banData = activeBans.get(playerUUID);
        if (banData != null && banData.getExpiryTimestamp() <= System.currentTimeMillis()) {
            removeBan(playerUUID);
            return null;
        }
        return banData;
    }

    public void removeBan(UUID playerUUID) {
        activeBans.remove(playerUUID);
        bansConfig.set("bans." + playerUUID.toString(), null);
        saveBansFile();
    }

    public void cleanupExpiredBans() {
        int removedCount = 0;
        for (UUID uuid : activeBans.keySet()) {
            CustomBanData data = activeBans.get(uuid);
            if (data != null && data.getExpiryTimestamp() <= System.currentTimeMillis()) {
                removeBan(uuid);
                removedCount++;
            }
        }
        if (removedCount > 0) {
            plugin.getLogger().info("清理任务：移除了 " + removedCount + " 条过期的自定义封禁记录。");
        }
    }

    public int incrementAndGetLoginAttempts(UUID playerUUID) {
        CustomBanData banData = activeBans.get(playerUUID);
        if (banData != null) {
            banData.incrementLoginAttempts();
            bansConfig.set("bans." + playerUUID.toString() + ".loginAttempts", banData.getLoginAttempts());
            saveBansFile();
            return banData.getLoginAttempts();
        }
        return 0;
    }
}