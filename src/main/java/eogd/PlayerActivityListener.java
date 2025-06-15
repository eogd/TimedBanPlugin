package eogd;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Date;
import java.util.UUID;

public class PlayerActivityListener implements Listener {

    private final TimedBanPlugin plugin;

    public PlayerActivityListener(TimedBanPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.isMarkedForRedirect(uuid)) {
            plugin.unmarkForRedirect(uuid);

            CustomBanData banData = plugin.getBanDataManager().getBan(uuid);

            if (banData != null && banData.getExpiryTimestamp() > System.currentTimeMillis()) {
                String bukkitBanReason = "Banned by TimedBanPlugin. Redirecting. Reason: " + banData.getOriginalReason();
                Date expiryDate = new Date(banData.getExpiryTimestamp());
                String bannerName = banData.getBannerName() != null ? banData.getBannerName() : "TimedBanPlugin";
                Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), bukkitBanReason, expiryDate, bannerName);

                ConfigurationSection redirectConfig = plugin.getConfig().getConfigurationSection("redirect-on-ban");
                if (redirectConfig == null) {
                    plugin.getLogger().warning("Redirect config section is missing for player " + player.getName());
                    return;
                }
                String jailServerName = redirectConfig.getString("server-name");
                String messageBeforeRedirect = redirectConfig.getString("kick-message-before-redirect");

                if (jailServerName == null || jailServerName.isEmpty()) {
                    plugin.getLogger().warning("server name is not configured for player " + player.getName());
                    return;
                }

                if (messageBeforeRedirect != null && !messageBeforeRedirect.isEmpty()) {
                    plugin.adventure().player(player).sendMessage(plugin.getMiniMessage().deserialize(messageBeforeRedirect));
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            plugin.sendPlayerToServer(player, jailServerName);
                        }
                    }
                }.runTaskLater(plugin, 20L);

            } else {
                plugin.getLogger().info("玩家 " + player.getName() + " 原本被标记跳转，但在加入时其封禁已失效或被移除。");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.isMarkedForRedirect(uuid)) {
            plugin.unmarkForRedirect(uuid);

            CustomBanData banData = plugin.getBanDataManager().getBan(uuid);
            if (banData != null && banData.getExpiryTimestamp() > System.currentTimeMillis()) {
                String bukkitBanReason = "Banned by TimedBanPlugin. Original reason: " + banData.getOriginalReason();
                Date expiryDate = new Date(banData.getExpiryTimestamp());
                String bannerName = banData.getBannerName() != null ? banData.getBannerName() : "TimedBanPlugin";

                Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), bukkitBanReason, expiryDate, bannerName);
                plugin.getLogger().info("玩家 " + player.getName() + " 被标记跳转但提前下线，已将其重新加入Bukkit封禁列表。");
            }
        }
    }
}