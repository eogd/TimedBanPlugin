package eogd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BanTimeCommand implements CommandExecutor {

    private final TimedBanPlugin plugin;

    public BanTimeCommand(TimedBanPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("timedban.use")) {
            Component message = plugin.getMiniMessage().deserialize("<red>你没有权限执行此命令。</red>");
            plugin.adventure().sender(sender).sendMessage(message);
            return true;
        }

        if (args.length < 3) {
            Component usage = plugin.getMiniMessage().deserialize("<red>用法: /bantime <玩家> <时间> [理由] <处决人></red>\n<gray>时间单位: d(天), h(小时), m(分钟), s(秒)。例如 1d, 2h, 30m。</gray>");
            plugin.adventure().sender(sender).sendMessage(usage);
            return true;
        }

        String targetName = args[0];
        String durationString = args[1];

        String bannerName;
        String reasonInput;

        if (args.length == 3) {
            bannerName = args[2];
            reasonInput = plugin.getConfig().getString("default-ban-reason", "未提供理由");
        } else {
            bannerName = args[args.length - 1];
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 2; i < args.length - 1; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reasonInput = reasonBuilder.toString().trim();
            if (reasonInput.isEmpty()) {
                reasonInput = plugin.getConfig().getString("default-ban-reason", "未提供理由");
            }
        }
        
        String finalReason = reasonInput.replace("/n", "\n");

        long durationMillis;
        try {
            durationMillis = parseDuration(durationString);
            if (durationMillis <= 0) {
                 Component invalidTime = plugin.getMiniMessage().deserialize("<red>无效的封禁时长: 必须为正数。</red>");
                 plugin.adventure().sender(sender).sendMessage(invalidTime);
                 return true;
            }
        } catch (IllegalArgumentException e) {
            Component invalidTime = plugin.getMiniMessage().deserialize("<red>无效的时间格式: " + e.getMessage() + "</red>\n<gray>示例: 1d (1天), 2h (2小时), 30m (30分钟)</gray>");
            plugin.adventure().sender(sender).sendMessage(invalidTime);
            return true;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
        Date expiryDate = new Date(System.currentTimeMillis() + durationMillis);
        String bukkitBanReason = bannerName + " 执行了定时封禁。理由: " + finalReason.split("\n")[0] + ". 详情见踢出信息。";
        
        Bukkit.getBanList(BanList.Type.NAME).addBan(targetPlayer.getName(), bukkitBanReason, expiryDate, sender.getName());
        plugin.getBanDataManager().addBan(targetPlayer.getUniqueId(), expiryDate.getTime(), finalReason, bannerName, durationString);

        String kickMessageTemplate = plugin.getConfig().getString("ban-kick-message", "<red>您已被封禁。</red>");
        String remainingTimeFormatted = formatDuration(durationMillis);

        TagResolver placeholders = TagResolver.builder()
                .resolver(Placeholder.component("reason", Component.text(finalReason)))
                .resolver(Placeholder.component("total_duration", Component.text(durationString)))
                .resolver(Placeholder.component("player_name", Component.text(targetPlayer.getName() != null ? targetPlayer.getName() : targetName)))
                .resolver(Placeholder.component("banner_name", Component.text(bannerName)))
                .resolver(Placeholder.component("remaining_time", Component.text(remainingTimeFormatted)))
                .build();

        Component kickComponent = plugin.getMiniMessage().deserialize(kickMessageTemplate, placeholders);

        if (targetPlayer.isOnline()) {
            Player onlineTarget = targetPlayer.getPlayer();
            if (onlineTarget != null) {
                 onlineTarget.kickPlayer(LegacyComponentSerializer.legacySection().serialize(kickComponent));
            }
        }
        
        Component successMessage = plugin.getMiniMessage().deserialize("<green>玩家 <white>" + (targetPlayer.getName() != null ? targetPlayer.getName() : targetName) + "</white> 已被封禁 <yellow>" + durationString + "</yellow>。理由: <gray>" + finalReason.replace("\n", " ") + "</gray></green>");
        plugin.adventure().sender(sender).sendMessage(successMessage);

        return true;
    }

    private long parseDuration(String durationStr) throws IllegalArgumentException {
        Pattern pattern = Pattern.compile("(\\d+)([dhms])");
        Matcher matcher = pattern.matcher(durationStr.toLowerCase());
        long totalMillis = 0;

        if (matcher.matches()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            switch (unit) {
                case "d": totalMillis = (long) value * 24 * 60 * 60 * 1000; break;
                case "h": totalMillis = (long) value * 60 * 60 * 1000; break;
                case "m": totalMillis = (long) value * 60 * 1000; break;
                case "s": totalMillis = (long) value * 1000; break;
                default: throw new IllegalArgumentException("未知的时间单位: " + unit);
            }
        } else {
            throw new IllegalArgumentException("格式错误 (例如 1d, 2h, 30m, 60s)");
        }
        if (totalMillis <= 0) throw new IllegalArgumentException("时长必须为正。");
        return totalMillis;
    }

    public static String formatDuration(long millis) {
        if (millis < 0) millis = 0;
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天 ");
        if (hours > 0) sb.append(hours).append("小时 ");
        if (minutes > 0) sb.append(minutes).append("分钟 ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("秒");
        return sb.toString().trim();
    }
}