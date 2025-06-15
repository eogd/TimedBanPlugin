package eogd;

import net.kyori.adventure.text.Component;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class TimeUnbanCommand implements CommandExecutor {

    private final TimedBanPlugin plugin;

    public TimeUnbanCommand(TimedBanPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("timedban.unban")) {
            Component message = plugin.getMiniMessage().deserialize("<red>你没有权限执行此命令。</red>");
            plugin.adventure().sender(sender).sendMessage(message);
            return true;
        }

        if (args.length != 1) {
            Component usage = plugin.getMiniMessage().deserialize("<red>用法: /timeunban <玩家></red>");
            plugin.adventure().sender(sender).sendMessage(usage);
            return true;
        }

        String targetName = args[0];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);

        boolean wasBukkitBanned = Bukkit.getBanList(BanList.Type.NAME).isBanned(targetPlayer.getName());
        CustomBanData customBanData = plugin.getBanDataManager().getBan(targetPlayer.getUniqueId());

        if (!wasBukkitBanned && customBanData == null) {
            Component notBannedMessage = plugin.getMiniMessage().deserialize("<yellow>玩家 <white>" + targetName + "</white> 当前未被此插件封禁。</yellow>");
            plugin.adventure().sender(sender).sendMessage(notBannedMessage);
            return true;
        }

        if (wasBukkitBanned) {
            Bukkit.getBanList(BanList.Type.NAME).pardon(targetPlayer.getName());
        }

        if (customBanData != null) {
            plugin.getBanDataManager().removeBan(targetPlayer.getUniqueId());
        }
        
        Component successMessage = plugin.getMiniMessage().deserialize("<green>玩家 <white>" + targetName + "</white> 的封禁已被解除。</green>");
        plugin.adventure().sender(sender).sendMessage(successMessage);

        return true;
    }
}