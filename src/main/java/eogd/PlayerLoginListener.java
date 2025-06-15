package eogd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class PlayerLoginListener implements Listener {

    private final TimedBanPlugin plugin;

    public PlayerLoginListener(TimedBanPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.KICK_BANNED) {
            CustomBanData banData = plugin.getBanDataManager().getBan(event.getPlayer().getUniqueId());

            if (banData != null && banData.getExpiryTimestamp() > System.currentTimeMillis()) {
                int attempts = plugin.getBanDataManager().incrementAndGetLoginAttempts(event.getPlayer().getUniqueId());

                if (attempts % 2 != 0) {
                    // 奇数
                    String kickMessageTemplate = plugin.getConfig().getString("ban-kick-message", "<red>您已被封禁。</red>");
                    long remainingMillis = banData.getExpiryTimestamp() - System.currentTimeMillis();
                    String remainingTimeFormatted = BanTimeCommand.formatDuration(remainingMillis);

                    TagResolver placeholders = TagResolver.builder()
                            .resolver(Placeholder.component("reason", Component.text(banData.getOriginalReason())))
                            .resolver(Placeholder.component("total_duration", Component.text(banData.getTotalDurationString())))
                            .resolver(Placeholder.component("player_name", Component.text(event.getPlayer().getName())))
                            .resolver(Placeholder.component("banner_name", Component.text(banData.getBannerName())))
                            .resolver(Placeholder.component("remaining_time", Component.text(remainingTimeFormatted)))
                            .build();

                    Component kickComponent = plugin.getMiniMessage().deserialize(kickMessageTemplate, placeholders);
                    event.setKickMessage(LegacyComponentSerializer.legacySection().serialize(kickComponent));
                } else {
                    // 偶数
                    Bukkit.getBanList(BanList.Type.NAME).pardon(event.getPlayer().getName());
                    event.allow();

                    ConfigurationSection redirectConfig = plugin.getConfig().getConfigurationSection("redirect-on-ban");
                    boolean redirectEnabled = false;
                    String redirectServerName = null;

                    if (redirectConfig != null) {
                        redirectEnabled = redirectConfig.getBoolean("enabled", false);
                        redirectServerName = redirectConfig.getString("server-name");
                    }

                    if (redirectEnabled && redirectServerName != null && !redirectServerName.isEmpty()) {
                        plugin.markForRedirect(event.getPlayer().getUniqueId());
                    } else {
                        plugin.getLogger().info("玩家 " + event.getPlayer().getName() + " (偶数次尝试) 已被允许进入服务器 (跳转未启用/配置)。");
                    }
                }
            } else if (banData != null && banData.getExpiryTimestamp() <= System.currentTimeMillis()) {
                Bukkit.getBanList(BanList.Type.NAME).pardon(event.getPlayer().getName());
                plugin.getBanDataManager().removeBan(event.getPlayer().getUniqueId());
                event.allow();
                plugin.getLogger().info("玩家 " + event.getPlayer().getName() + " 的定时封禁已到期，已自动解封。");
            }
        }
    }
}