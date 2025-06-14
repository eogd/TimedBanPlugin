package com.example.myhunterplugin;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public class HunterCommandExecutor implements CommandExecutor {

    private final HunterPlugin plugin;
    private final String NAMEPLATE_VISIBLE_KEY = "nameplate-visible";


    public HunterCommandExecutor(HunterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("hunter")) {
            return false;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set":
                handleSetCommand(sender, args);
                break;
            case "reset":
                handleResetCommand(sender);
                break;
            case "npc":
                handleNpcCommand(sender);
                break;
            case "start":
                handleStartCommand(sender);
                break;
            case "loser":
                handleLoserCommand(sender);
                break;
            case "king":
                handleKingCommand(sender, args);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "未知的子命令。使用 /hunter 查看帮助。");
                break;
        }
        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Hunter 插件帮助 ---");
        if (sender.hasPermission("hunter.admin")) {
            sender.sendMessage(ChatColor.AQUA + "/hunter set <玩家ID>" + ChatColor.GRAY + " - 设置玩家为猎人");
            sender.sendMessage(ChatColor.AQUA + "/hunter reset" + ChatColor.GRAY + " - 重置并清理游戏");
            sender.sendMessage(ChatColor.AQUA + "/hunter npc" + ChatColor.GRAY + " - 放置任务NPC");
            sender.sendMessage(ChatColor.AQUA + "/hunter start" + ChatColor.GRAY + " - 开始游戏");
        }
        if (sender.hasPermission("hunter.player")) {
            sender.sendMessage(ChatColor.GREEN + "/hunter loser" + ChatColor.GRAY + " - 主动弃权");
            sender.sendMessage(ChatColor.GREEN + "/hunter king <on|off>" + ChatColor.GRAY + " - 开/关侧边栏公告榜");
        }
        if (!sender.hasPermission("hunter.admin") && !sender.hasPermission("hunter.player")) {
            sender.sendMessage(ChatColor.RED + "您没有权限使用此插件的任何命令。");
        }
    }

    private void handleSetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hunter.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /hunter set <玩家ID>");
            return;
        }
        String playerName = args[1];
        Player targetPlayer = Bukkit.getPlayerExact(playerName);

        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "玩家 " + playerName + " 不在线或不存在。");
            return;
        }

        if (plugin.getCurrentPhase() != HunterPlugin.GamePhase.WAITING) {
            sender.sendMessage(ChatColor.RED + "游戏正在进行或准备中，无法修改玩家身份。请先通过 /hunter reset 结束并清理当前游戏。");
            return;
        }

        if (plugin.isHunter(targetPlayer.getUniqueId())) {
            sender.sendMessage(ChatColor.YELLOW + "玩家 " + targetPlayer.getName() + " 已经是猎人了。");
            return;
        }

        plugin.addHunter(targetPlayer.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + "已成功将玩家 " + targetPlayer.getName() + " 设置为猎人。");
        targetPlayer.sendMessage(ChatColor.GOLD + "你已被设置为猎人！");
    }

    private void handleResetCommand(CommandSender sender) {
        if (!sender.hasPermission("hunter.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令。");
            return;
        }

        if (plugin.getCurrentPhase() == HunterPlugin.GamePhase.RUNNING || plugin.getCurrentPhase() == HunterPlugin.GamePhase.PREPARATION) {
            plugin.endGameCleanup(true, "管理员强制重置");
            sender.sendMessage(ChatColor.YELLOW + "游戏已被强制结束并重置。");
        } else {
            plugin.endGameCleanup(false, "管理员重置");
            plugin.clearHunters();
            plugin.clearRunners();
            sender.sendMessage(ChatColor.GREEN + "已成功重置所有游戏身份并清理游戏状态。");
        }
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "所有游戏身份及状态已被管理员重置！");
    }

    private void handleNpcCommand(CommandSender sender) {
        if (!sender.hasPermission("hunter.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令。");
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行。");
            return;
        }
        Player admin = (Player) sender;
        NPCRegistry registry = plugin.getNpcRegistry();

        if (registry == null) {
            admin.sendMessage(ChatColor.RED + "Citizens插件未连接或未正常工作，无法创建NPC。");
            return;
        }

        String npcNameFromConfig = plugin.getPluginConfig().getString("npc_interaction_task.npc_display_name", "任务NPC");
        String coloredNpcName = ChatColor.translateAlternateColorCodes('&', npcNameFromConfig);
        Location npcLocation = admin.getLocation();

        try {
            NPC npc = registry.createNPC(EntityType.PLAYER, coloredNpcName);
            npc.spawn(npcLocation);
            npc.data().set("hunter_quest_npc", true);
            npc.data().set(NAMEPLATE_VISIBLE_KEY, true);
            admin.sendMessage(ChatColor.GREEN + "已在您的位置成功创建名为 '" + coloredNpcName + ChatColor.GREEN + "' 的任务NPC (ID: " + npc.getId() + ")。");
        } catch (Exception e) {
            admin.sendMessage(ChatColor.RED + "创建NPC时发生错误。");
            plugin.getLogger().severe("创建NPC时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleStartCommand(CommandSender sender) {
        if (!sender.hasPermission("hunter.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令。");
            return;
        }

        if (plugin.getCurrentPhase() != HunterPlugin.GamePhase.WAITING) {
            sender.sendMessage(ChatColor.RED + "游戏已在进行中或正在清理上一局！请使用 /hunter reset 完全重置（如果需要）。");
            return;
        }

        if (plugin.getHunters().isEmpty()) {
            sender.sendMessage(ChatColor.RED + "无法开始游戏：没有设置任何猎人。请使用 /hunter set <玩家ID> 设置猎人。");
            return;
        }

        int minRunners = plugin.getPluginConfig().getInt("game_flow.min_runners_to_start", 1);
        long onlinePlayerCount = Bukkit.getOnlinePlayers().stream().filter(p -> !plugin.isHunter(p.getUniqueId())).count();

        if (onlinePlayerCount < minRunners) {
            sender.sendMessage(ChatColor.RED + "无法开始游戏：逃亡者数量不足 (需要 " + minRunners + " 名，当前 " + onlinePlayerCount + " 名非猎人玩家)。");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "游戏开始程序已启动...");
        plugin.initiateGameStartProcedure();
    }

    private void handleLoserCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行。");
            return;
        }
        if (!sender.hasPermission("hunter.player")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令。");
            return;
        }
        Player player = (Player) sender;

        if (plugin.getCurrentPhase() != HunterPlugin.GamePhase.RUNNING && plugin.getCurrentPhase() != HunterPlugin.GamePhase.PREPARATION) {
            player.sendMessage(ChatColor.RED + "游戏当前不处于可弃权的阶段。");
            return;
        }
        plugin.playerForfeit(player);
    }

    private void handleKingCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行。");
            return;
        }
        if (!sender.hasPermission("hunter.player")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令。");
            return;
        }
        if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            sender.sendMessage(ChatColor.RED + "用法: /hunter king <on|off>");
            return;
        }

        Player player = (Player) sender;
        String toggle = args[1].toLowerCase();

        if (toggle.equals("on")) {
            plugin.setScoreboardStatus(player.getUniqueId(), true);
            player.sendMessage(ChatColor.GREEN + "侧边栏公告榜已开启。");
        } else {
            plugin.setScoreboardStatus(player.getUniqueId(), false);
            player.sendMessage(ChatColor.YELLOW + "侧边栏公告榜已关闭。");
        }
    }
}