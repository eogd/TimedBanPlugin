package com.example.myhunterplugin;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

public class HunterPlugin extends JavaPlugin implements Listener {

    private static HunterPlugin instance;
    private FileConfiguration config;
    private NPCRegistry npcRegistry = null;

    private final Set<UUID> hunters = new HashSet<>();
    private final Set<UUID> runners = new HashSet<>();
    private boolean gameInProgress = false;

    private final Map<UUID, Integer> playerPoints = new HashMap<>();
    private boolean pointsSystemEnabled;
    private int passivePointsPerSecond;
    private int pointsPerNpcTask;
    private int pointsPerFlagPlace;
    private int pointsPerRunnerCaught;
    private String pointsDisplayFormat;
    private int rankingDisplayCount;
    private String rankingHeader;
    private String rankingFormat;


    private final Map<UUID, Boolean> scoreboardStatus = new HashMap<>();

    public enum GamePhase {
        WAITING, PREPARATION, RUNNING, FINISHED
    }
    private GamePhase currentPhase = GamePhase.WAITING;
    private BukkitTask preparationTask = null;
    private BukkitTask gameTimerTask = null;
    private int preparationTimeRemaining;
    private int gameTimeRemaining;

    private Set<UUID> npcTaskCompletedPlayers = new HashSet<>();
    private int npcTaskMaxCompletions;
    private boolean npcPasswordEnabled;
    private String npcPassword;
    private String npcPasswordPrompt;
    private String npcPasswordCorrect;
    private String npcPasswordIncorrect;
    private final Set<UUID> playersAwaitingPassword = new HashSet<>();


    private boolean flagTaskEnabled;
    private int requiredFlagsToPlace;
    private List<Location> flagTargetPlacementLocations = new ArrayList<>();
    private Set<Location> occupiedFlagTargets = new HashSet<>();
    private int flagsSuccessfullyPlacedCount = 0;
    private Material bannerMaterialToPickup;
    private Sound bannerPickupSound;
    private float bannerPickupVolume;
    private float bannerPickupPitch;
    private Sound bannerPlaceSound;
    private float bannerPlaceVolume;
    private float bannerPlacePitch;
    private Sound bannerPlaceFailSound;
    private float bannerPlaceFailVolume;
    private float bannerPlaceFailPitch;
    private ItemStack flagTaskItem;

    private ItemStack hunterWeapon;
    private String cannotDropWeaponMessage;
    private Location jailLocation;
    private Location spectatorSpawnLocation;


    private final String NAMEPLATE_VISIBLE_KEY = "nameplate-visible";


    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = getConfig();
        loadConfigValues();

        if (!setupCitizens()) {
            getLogger().severe("未找到 Citizens 插件或未能成功挂钩。NPC 相关功能将不可用。");
        } else {
            getLogger().info("成功挂钩 Citizens 插件。");
        }

        registerCommands();
        registerEvents();
        getLogger().info(ChatColor.GREEN + "MyHunterPlugin 已成功启用！");
    }

    private void loadConfigValues() {
        npcTaskMaxCompletions = getPluginConfig().getInt("npc_interaction_task.max_completions", 10);
        npcPasswordEnabled = getPluginConfig().getBoolean("npc_interaction_task.password_enabled", false);
        npcPassword = getPluginConfig().getString("npc_interaction_task.password", "changeme");
        npcPasswordPrompt = ChatColor.translateAlternateColorCodes('&', getPluginConfig().getString("npc_interaction_task.password_prompt_message", "&e请输入NPC的交互密码: "));
        npcPasswordCorrect = ChatColor.translateAlternateColorCodes('&', getPluginConfig().getString("npc_interaction_task.password_correct_message", "&a密码正确！正在处理你的任务..."));
        npcPasswordIncorrect = ChatColor.translateAlternateColorCodes('&', getPluginConfig().getString("npc_interaction_task.password_incorrect_message", "&c密码错误！交互失败。"));


        flagTaskEnabled = getPluginConfig().getBoolean("flag_task.enabled", true);
        requiredFlagsToPlace = getPluginConfig().getInt("flag_task.required_flags_to_place", 3);
        flagTargetPlacementLocations.clear();
        List<String> locStrings = getPluginConfig().getStringList("flag_task.target_placement_locations");
        for (String s : locStrings) {
            Location loc = parseLocationFromString(s, "flag_task.target_placement_locations");
            if (loc != null) {
                flagTargetPlacementLocations.add(loc.getBlock().getLocation());
            }
        }
        String bannerMatName = getPluginConfig().getString("flag_task.banner_material_to_pickup", "WHITE_BANNER").toUpperCase();
        bannerMaterialToPickup = Material.getMaterial(bannerMatName);
        if (bannerMaterialToPickup == null || !bannerMaterialToPickup.name().contains("BANNER")) {
            getLogger().warning("配置的旗帜拾取类型 '" + bannerMatName + "' 无效或不是旗帜, 将使用 WHITE_BANNER。");
            bannerMaterialToPickup = Material.WHITE_BANNER;
        }

        flagTaskItem = new ItemStack(bannerMaterialToPickup, 1);
        ItemMeta flagMeta = flagTaskItem.getItemMeta();
        if (flagMeta != null) {
            flagMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6任务旗帜"));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', "&e将其放置到指定目标点！"));
            flagMeta.setLore(lore);
            flagTaskItem.setItemMeta(flagMeta);
        }

        String[] pickupSoundParts = getPluginConfig().getString("flag_task.banner_pickup_sound", "ENTITY_ITEM_PICKUP,1.0,1.0").split(",");
        try {
            bannerPickupSound = Sound.valueOf(pickupSoundParts[0].toUpperCase());
            bannerPickupVolume = pickupSoundParts.length > 1 ? Float.parseFloat(pickupSoundParts[1]) : 1.0f;
            bannerPickupPitch = pickupSoundParts.length > 2 ? Float.parseFloat(pickupSoundParts[2]) : 1.0f;
        } catch (IllegalArgumentException e) {
            getLogger().warning("旗帜拾取声音配置无效: " + getPluginConfig().getString("flag_task.banner_pickup_sound"));
            bannerPickupSound = Sound.ENTITY_ITEM_PICKUP; bannerPickupVolume = 1.0f; bannerPickupPitch = 1.0f;
        }

        String[] placeSoundParts = getPluginConfig().getString("flag_task.banner_place_sound", "BLOCK_NOTE_BLOCK_PLING,1.0,1.2").split(",");
        try {
            bannerPlaceSound = Sound.valueOf(placeSoundParts[0].toUpperCase());
            bannerPlaceVolume = placeSoundParts.length > 1 ? Float.parseFloat(placeSoundParts[1]) : 1.0f;
            bannerPlacePitch = placeSoundParts.length > 2 ? Float.parseFloat(placeSoundParts[2]) : 1.2f;
        } catch (IllegalArgumentException e) {
            getLogger().warning("旗帜放置成功声音配置无效: " + getPluginConfig().getString("flag_task.banner_place_sound"));
            bannerPlaceSound = Sound.BLOCK_NOTE_BLOCK_PLING; bannerPlaceVolume = 1.0f; bannerPlacePitch = 1.2f;
        }
        String[] placeFailSoundParts = getPluginConfig().getString("flag_task.banner_place_fail_sound", "BLOCK_ANVIL_LAND,0.5,1.0").split(",");
        try {
            bannerPlaceFailSound = Sound.valueOf(placeFailSoundParts[0].toUpperCase());
            bannerPlaceFailVolume = placeFailSoundParts.length > 1 ? Float.parseFloat(placeFailSoundParts[1]) : 0.5f;
            bannerPlaceFailPitch = placeFailSoundParts.length > 2 ? Float.parseFloat(placeFailSoundParts[2]) : 1.0f;
        } catch (IllegalArgumentException e) {
            getLogger().warning("旗帜放置失败声音配置无效: " + getPluginConfig().getString("flag_task.banner_place_fail_sound"));
            bannerPlaceFailSound = Sound.BLOCK_ANVIL_LAND; bannerPlaceFailVolume = 0.5f; bannerPlaceFailPitch = 1.0f;
        }

        String hunterWeaponMaterialName = getPluginConfig().getString("hunter_settings.weapon.material", "DIAMOND_SWORD").toUpperCase();
        Material hunterWeaponMat = Material.getMaterial(hunterWeaponMaterialName);
        if (hunterWeaponMat == null) {
            getLogger().warning("猎人武器材质 '" + hunterWeaponMaterialName + "' 无效，将使用钻石剑。");
            hunterWeaponMat = Material.DIAMOND_SWORD;
        }
        hunterWeapon = new ItemStack(hunterWeaponMat);
        ItemMeta weaponMeta = hunterWeapon.getItemMeta();
        if (weaponMeta != null) {
            String weaponName = getPluginConfig().getString("hunter_settings.weapon.name", "&c猎杀之刃");
            weaponMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', weaponName));
            List<String> weaponLoreConfig = getPluginConfig().getStringList("hunter_settings.weapon.lore");
            if (weaponLoreConfig != null && !weaponLoreConfig.isEmpty()) {
                weaponMeta.setLore(weaponLoreConfig.stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList()));
            }
            weaponMeta.setUnbreakable(true);
            weaponMeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hunterWeapon.setItemMeta(weaponMeta);
        }
        cannotDropWeaponMessage = ChatColor.translateAlternateColorCodes('&', getPluginConfig().getString("hunter_settings.cannot_drop_weapon_message", "&c作为猎人，你无法丢弃你的狩猎工具！"));

        jailLocation = parseLocationFromString(getPluginConfig().getString("hunter_settings.jail_location"), "hunter_settings.jail_location");
        spectatorSpawnLocation = parseLocationFromString(getPluginConfig().getString("spawn_points.spectator"), "spawn_points.spectator");
        if (spectatorSpawnLocation == null) {
            getLogger().warning("旁观者出生点 (spawn_points.spectator) 未配置或配置无效，被抓捕的逃亡者可能不会被正确传送。");
        }


        pointsSystemEnabled = getPluginConfig().getBoolean("points_system.enabled", true);
        passivePointsPerSecond = getPluginConfig().getInt("points_system.passive_points_per_second", 1);
        pointsPerNpcTask = getPluginConfig().getInt("points_system.points_per_npc_task_completion", 10);
        pointsPerFlagPlace = getPluginConfig().getInt("points_system.points_per_flag_placed", 25);
        pointsPerRunnerCaught = getPluginConfig().getInt("points_system.points_per_runner_caught", 50);
        pointsDisplayFormat = getPluginConfig().getString("points_system.display_format", "&e[{points}] &r{player}");
        rankingDisplayCount = getPluginConfig().getInt("points_system.ranking_display_count", 5);
        rankingHeader = ChatColor.translateAlternateColorCodes('&', getPluginConfig().getString("points_system.ranking_broadcast_header", "&6&l游戏结束！最终积分排名："));
        rankingFormat = ChatColor.translateAlternateColorCodes('&', getPluginConfig().getString("points_system.ranking_broadcast_format", "&e{rank}. &r{player} &7- &a{points} 分"));
    }

    @Override
    public void onDisable() {
        if (currentPhase != GamePhase.WAITING) {
            endGameCleanup(false, "服务器关闭");
        }
        hunters.clear();
        runners.clear();
        npcTaskCompletedPlayers.clear();
        occupiedFlagTargets.clear();
        flagsSuccessfullyPlacedCount = 0;
        playerPoints.clear();
        scoreboardStatus.clear();
        playersAwaitingPassword.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetPlayerNameAndTab(player);
            if (player.getScoreboard() != null && player.getScoreboard().getObjective("hunterGameSidebar") != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        getLogger().info(ChatColor.RED + "MyHunterPlugin 已禁用。");
    }

    private boolean setupCitizens() {
        PluginManager pm = Bukkit.getPluginManager();
        if (pm.getPlugin("Citizens") == null || !pm.getPlugin("Citizens").isEnabled()) {
            return false;
        }
        try {
            npcRegistry = CitizensAPI.getNPCRegistry();
        } catch (NoClassDefFoundError e) {
            return false;
        }
        return npcRegistry != null;
    }

    private void registerCommands() {
        this.getCommand("hunter").setExecutor(new HunterCommandExecutor(this));
    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
    }

    public static HunterPlugin getInstance() {
        return instance;
    }

    public FileConfiguration getPluginConfig() {
        return config;
    }

    public NPCRegistry getNpcRegistry() {
        return npcRegistry;
    }

    public GamePhase getCurrentPhase() {
        return currentPhase;
    }

    public void addHunter(UUID playerUUID) {
        hunters.add(playerUUID);
        runners.remove(playerUUID);
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            initializePlayerPoints(player);
            updatePlayerNameAndTab(player);
            updatePlayerSidebarScoreboard(player.getUniqueId());
        }
        updateAllPlayerSideBarScoreboards();
    }

    public void removeHunter(UUID playerUUID) {
        hunters.remove(playerUUID);
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            resetPlayerNameAndTab(player);
            updatePlayerSidebarScoreboard(player.getUniqueId());
        }
        updateAllPlayerSideBarScoreboards();
    }

    public boolean isHunter(UUID playerUUID) {
        return hunters.contains(playerUUID);
    }

    public void clearHunters() {
        Set<UUID> oldHunters = new HashSet<>(hunters);
        hunters.clear();
        oldHunters.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                resetPlayerNameAndTab(p);
                updatePlayerSidebarScoreboard(p.getUniqueId());
            }
        });
        updateAllPlayerSideBarScoreboards();
    }

    public Set<UUID> getHunters() {
        return new HashSet<>(hunters);
    }

    public void addRunner(UUID playerUUID) {
        runners.add(playerUUID);
        hunters.remove(playerUUID);
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            initializePlayerPoints(player);
            updatePlayerNameAndTab(player);
            updatePlayerSidebarScoreboard(player.getUniqueId());
        }
        updateAllPlayerSideBarScoreboards();
    }

    public void removeRunner(UUID playerUUID) {
        runners.remove(playerUUID);
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            resetPlayerNameAndTab(player);
            updatePlayerSidebarScoreboard(player.getUniqueId());
        }
        updateAllPlayerSideBarScoreboards();
    }

    public boolean isRunner(UUID playerUUID) {
        return runners.contains(playerUUID);
    }

    public void clearRunners() {
        Set<UUID> oldRunners = new HashSet<>(runners);
        runners.clear();
        oldRunners.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                resetPlayerNameAndTab(p);
                updatePlayerSidebarScoreboard(p.getUniqueId());
            }
        });
        updateAllPlayerSideBarScoreboards();
    }
    public Set<UUID> getRunners() {
        return new HashSet<>(runners);
    }

    public void setGameGlobalState(boolean inProgress, GamePhase phase) {
        this.gameInProgress = inProgress;
        this.currentPhase = phase;
        if (phase == GamePhase.WAITING || phase == GamePhase.FINISHED) {
            playersAwaitingPassword.clear();
        }
        updateAllPlayerNamesAndTabs();
        updateAllPlayerSideBarScoreboards();
    }

    public Location parseLocationFromString(String locString, String pathForError) {
        if (locString == null || locString.trim().isEmpty()) {
            getLogger().warning("解析位置失败：配置文件路径 '" + pathForError + "' 的坐标字符串为空。");
            return null;
        }
        String[] parts = locString.split(",");
        if (parts.length < 4) {
            getLogger().warning("解析位置失败：配置文件路径 '" + pathForError + "' 的坐标格式不正确 (需要 世界名,x,y,z[,yaw,pitch]): " + locString);
            return null;
        }

        World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            String defaultWorldName = Bukkit.getWorlds().get(0).getName();
            getLogger().warning("解析位置失败：配置文件路径 '" + pathForError + "' 中指定的世界 '" + parts[0] + "' 未找到，将使用默认主世界: " + defaultWorldName);
            world = Bukkit.getWorlds().get(0);
        }

        try {
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            float yaw = (parts.length >= 5) ? Float.parseFloat(parts[4].trim()) : 0;
            float pitch = (parts.length >= 6) ? Float.parseFloat(parts[5].trim()) : 0;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            getLogger().warning("解析位置失败：配置文件路径 '" + pathForError + "' 的坐标包含无效数字: " + locString);
            return null;
        }
    }

    public ItemStack getIdentityCard() {
        String materialName = getPluginConfig().getString("npc_interaction_task.identity_card.material", "PAPER").toUpperCase();
        Material itemMaterial = Material.getMaterial(materialName);
        if (itemMaterial == null) {
            itemMaterial = Material.PAPER;
        }
        ItemStack idCard = new ItemStack(itemMaterial);
        ItemMeta meta = idCard.getItemMeta();
        if (meta != null) {
            String displayName = getPluginConfig().getString("npc_interaction_task.identity_card.display_name", "&b身份卡");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            List<String> loreConfig = getPluginConfig().getStringList("npc_interaction_task.identity_card.lore");
            if (loreConfig != null && !loreConfig.isEmpty()) {
                List<String> lore = new ArrayList<>();
                for (String line : loreConfig) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(lore);
            }
            idCard.setItemMeta(meta);
        }
        return idCard;
    }

    public void initiateGameStartProcedure() {
        playerPoints.clear();
        npcTaskCompletedPlayers.clear();
        occupiedFlagTargets.clear();
        flagsSuccessfullyPlacedCount = 0;
        playersAwaitingPassword.clear();
        loadConfigValues();

        setGameGlobalState(true, GamePhase.PREPARATION);
        preparationTimeRemaining = getPluginConfig().getInt("time_settings.preparation", 30);
        gameTimeRemaining = getPluginConfig().getInt("time_settings.game_duration", 600);

        Location hunterSpawn = parseLocationFromString(getPluginConfig().getString("spawn_points.hunters"), "spawn_points.hunters");
        Location runnerSpawn = parseLocationFromString(getPluginConfig().getString("spawn_points.runners"), "spawn_points.runners");
        ItemStack identityCard = getIdentityCard();

        for (Player player : Bukkit.getOnlinePlayers()) {
            initializePlayerPoints(player);
            scoreboardStatus.putIfAbsent(player.getUniqueId(), getPluginConfig().getBoolean("scoreboard.default_on_join", true));
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            player.setSaturation(5.0F);
            player.setExp(0f);
            player.setLevel(0);

            if (isHunter(player.getUniqueId())) {
                if (hunterSpawn != null) player.teleport(hunterSpawn);
                player.sendMessage(ChatColor.RED + "你是猎人！准备阶段开始！");
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, preparationTimeRemaining * 20 + 40, 255, false, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, preparationTimeRemaining * 20 + 40, 128, false, false, false));
            } else {
                if(!isRunner(player.getUniqueId())) addRunner(player.getUniqueId());
                if (runnerSpawn != null) player.teleport(runnerSpawn);
                player.getInventory().addItem(identityCard.clone());
                player.sendMessage(ChatColor.GREEN + "你是逃亡者！准备阶段开始，尽快熟悉环境！");
            }
        }
        updateAllPlayerNamesAndTabs();
        updateAllPlayerSideBarScoreboards();
        startPreparationTimer();
    }

    private void startPreparationTimer() {
        if (preparationTask != null) preparationTask.cancel();
        preparationTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (currentPhase != GamePhase.PREPARATION) {
                if(preparationTask!=null) preparationTask.cancel();
                return;
            }
            if (preparationTimeRemaining <= 0) {
                preparationTask.cancel();
                preparationTask = null;
                startMainGame();
                return;
            }
            if (preparationTimeRemaining % 10 == 0 || preparationTimeRemaining <= 5) {
                Bukkit.broadcastMessage(ChatColor.AQUA + "准备阶段剩余: " + ChatColor.YELLOW + formatTime(preparationTimeRemaining));
            }
            updateAllPlayerNamesAndTabs();
            updateAllPlayerSideBarScoreboards();
            preparationTimeRemaining--;
        }, 0L, 20L);
    }

    private void startMainGame() {
        setGameGlobalState(true, GamePhase.RUNNING);
        Bukkit.broadcastMessage(ChatColor.GOLD + "准备阶段结束！游戏正式开始！逃亡者快跑！");
        for (UUID hunterUUID : getHunters()) {
            Player hunter = Bukkit.getPlayer(hunterUUID);
            if (hunter != null) {
                hunter.removePotionEffect(PotionEffectType.SLOWNESS);
                hunter.removePotionEffect(PotionEffectType.JUMP_BOOST);
                if (hunterWeapon != null) {
                    ItemStack currentHunterWeapon = hunterWeapon.clone();
                    ItemMeta meta = currentHunterWeapon.getItemMeta();
                    if (meta != null) {
                        meta.setUnbreakable(true);
                        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                        currentHunterWeapon.setItemMeta(meta);
                    }
                    hunter.getInventory().addItem(currentHunterWeapon);
                }
                hunter.sendMessage(ChatColor.GREEN + "你可以自由行动了！开始追捕！");
            }
        }
        if (gameTimerTask != null) gameTimerTask.cancel();
        gameTimerTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (currentPhase != GamePhase.RUNNING) {
                if(gameTimerTask!=null) gameTimerTask.cancel();
                return;
            }
            if (gameTimeRemaining <= 0) {
                gameTimerTask.cancel();
                gameTimerTask = null;
                endGameByTimeOut();
                return;
            }

            if (passivePointsPerSecond > 0) {
                for (UUID hunterUUID : getHunters()) {
                    Player hunter = Bukkit.getPlayer(hunterUUID);
                    if (hunter != null && hunter.isOnline() && hunter.getGameMode() == GameMode.SURVIVAL) {
                        addPoints(hunterUUID, passivePointsPerSecond);
                    }
                }
                for (UUID runnerUUID : getRunners()) {
                    Player runner = Bukkit.getPlayer(runnerUUID);
                    if (runner != null && runner.isOnline() && runner.getGameMode() == GameMode.SURVIVAL) {
                        addPoints(runnerUUID, passivePointsPerSecond);
                    }
                }
            }

            if (gameTimeRemaining % 60 == 0 || gameTimeRemaining <= 10 || (gameTimeRemaining <= 300 && gameTimeRemaining % 30 == 0) ) {
                Bukkit.broadcastMessage(ChatColor.GOLD + "游戏剩余时间: " + ChatColor.YELLOW + formatTime(gameTimeRemaining));
            }
            updateAllPlayerNamesAndTabs();
            updateAllPlayerSideBarScoreboards();
            gameTimeRemaining--;
        }, 0L, 20L);
    }

    private void endGameByTimeOut() {
        Bukkit.broadcastMessage(ChatColor.GOLD + "时间到！");
        applyGlowingToUnfinishedNPCTaskRunners();

        boolean flagTaskCompleted = flagsSuccessfullyPlacedCount >= requiredFlagsToPlace;

        if (flagTaskEnabled && !flagTaskCompleted) {
            String failMsg = getPluginConfig().getString("flag_task.task_fail_broadcast", "&6[团队任务] &c逃亡者未能及时收集所有旗帜！");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', failMsg));
        }

        if (!getRunners().isEmpty()) {
            if (flagTaskEnabled) {
                if (flagTaskCompleted) {
                    Bukkit.broadcastMessage(ChatColor.GREEN + "逃亡者成功存活并且完成了旗帜任务！逃亡者胜利！");
                } else {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "逃亡者虽然存活，但未能完成旗帜任务！猎人胜利！");
                }
            } else {
                Bukkit.broadcastMessage(ChatColor.GREEN + "逃亡者成功存活！逃亡者胜利！");
            }
        } else {
            Bukkit.broadcastMessage(ChatColor.RED + "所有逃亡者均已被捕或弃权！猎人胜利！");
        }
        endGameCleanup(true, "时间耗尽");
    }

    private void applyGlowingToUnfinishedNPCTaskRunners() {
        if (npcTaskCompletedPlayers.size() >= npcTaskMaxCompletions || currentPhase == GamePhase.FINISHED) {
            int glowingDurationTicks = getPluginConfig().getInt("npc_interaction_task.glowing_duration_seconds", 60) * 20;
            for (UUID runnerUUID : getRunners()) {
                if (!npcTaskCompletedPlayers.contains(runnerUUID)) {
                    Player runner = Bukkit.getPlayer(runnerUUID);
                    if (runner != null && runner.isOnline()) {
                        runner.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowingDurationTicks, 0, false, true, true));
                        runner.sendMessage(ChatColor.YELLOW + "由于NPC任务名额已满或时间结束，你未能及时完成，现在你会发光！");
                    }
                }
            }
            if(npcTaskCompletedPlayers.size() >= npcTaskMaxCompletions && currentPhase != GamePhase.FINISHED) {
                Bukkit.broadcastMessage(ChatColor.AQUA + "NPC交互任务名额已全部完成！");
            }
        }
    }

    private void announceAndLogRankings() {
        if (playerPoints.isEmpty()) {
            Bukkit.broadcastMessage(rankingHeader);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "本局没有玩家获得积分。");
            return;
        }

        List<Map.Entry<UUID, Integer>> sortedPlayers = new ArrayList<>(playerPoints.entrySet());
        sortedPlayers.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        Bukkit.broadcastMessage(rankingHeader);
        getLogger().info(ChatColor.stripColor(rankingHeader));

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sortedPlayers) {
            if (rank > rankingDisplayCount && rankingDisplayCount > 0) break;
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "未知玩家 (" + entry.getKey().toString().substring(0,6) + ")";
            String rankMessage = rankingFormat
                    .replace("{rank}", String.valueOf(rank))
                    .replace("{player}", playerName)
                    .replace("{points}", String.valueOf(entry.getValue()));
            Bukkit.broadcastMessage(rankMessage);
            getLogger().info(ChatColor.stripColor(rankMessage));
            rank++;
        }
    }


    public void endGameCleanup(boolean broadcastMessage, String reason) {
        if (preparationTask != null) {
            preparationTask.cancel();
            preparationTask = null;
        }
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }

        applyGlowingToUnfinishedNPCTaskRunners();

        announceAndLogRankings();

        setGameGlobalState(false, GamePhase.FINISHED);

        Location endLocation = parseLocationFromString(getPluginConfig().getString("spawn_points.end_game_teleport_location"), "spawn_points.end_game_teleport_location");
        boolean teleportOnEnd = getPluginConfig().getBoolean("game_flow.teleport_on_game_end", true);

        Set<UUID> originalHunters = new HashSet<>(hunters);
        Set<UUID> originalRunners = new HashSet<>(runners);

        for (Player p : Bukkit.getOnlinePlayers()){
            boolean wasHunter = originalHunters.contains(p.getUniqueId());
            boolean wasRunner = originalRunners.contains(p.getUniqueId());
            boolean wasParticipant = wasHunter || wasRunner;

            p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
            p.setGlowing(false);
            resetPlayerNameAndTab(p);
            if (p.getScoreboard() != null && p.getScoreboard().getObjective("hunterGameSidebar") != null) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }

            if (wasParticipant) {
                p.setGameMode(GameMode.SURVIVAL);
                p.getInventory().clear();
                p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                p.setFoodLevel(20);
                p.setSaturation(5.0F);
                p.setExp(0f);
                p.setLevel(0);
                if (teleportOnEnd && endLocation != null) {
                    p.teleport(endLocation);
                }
            }
        }

        clearHunters();
        clearRunners();
        npcTaskCompletedPlayers.clear();
        occupiedFlagTargets.clear();
        flagsSuccessfullyPlacedCount = 0;
        playerPoints.clear();
        playersAwaitingPassword.clear();

        if (broadcastMessage) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "本轮猎人游戏因“" + reason + "”已结束！感谢您的参与！");
        }
        currentPhase = GamePhase.WAITING;
        updateAllPlayerNamesAndTabs();
        updateAllPlayerSideBarScoreboards();
        getLogger().info("游戏已结束，状态已清理。原因: " + reason);
    }

    public void playerForfeit(Player player) {
        if (currentPhase != GamePhase.RUNNING && currentPhase != GamePhase.PREPARATION) return;

        if (isRunner(player.getUniqueId())) {
            handleRunnerCaught(player, null, "主动弃权");
        } else if (isHunter(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "猎人目前无法使用弃权命令。");
        } else {
            player.sendMessage(ChatColor.RED + "你当前没有游戏身份可以弃权。");
        }
    }

    private void handleRunnerCaught(Player runner, Player hunter, String catchReason) {
        if (!isRunner(runner.getUniqueId()) || (currentPhase != GamePhase.RUNNING && currentPhase != GamePhase.PREPARATION && !catchReason.equals("退出游戏"))) return;

        removeRunner(runner.getUniqueId());
        runner.setGameMode(GameMode.SPECTATOR);

        if (spectatorSpawnLocation != null) {
            runner.teleport(spectatorSpawnLocation);
            runner.sendMessage(ChatColor.GRAY + "你已被传送至旁观区域。");
        } else if (jailLocation != null) {
            runner.teleport(jailLocation);
            runner.sendMessage(ChatColor.GRAY + "你已被送往旁观区域 (备用点)。");
        } else {
            runner.sendMessage(ChatColor.GRAY + "你现在是旁观者。");
        }
        updatePlayerSidebarScoreboard(runner.getUniqueId());

        if (hunter != null) {
            Bukkit.broadcastMessage(ChatColor.AQUA + runner.getName() + ChatColor.RED + " 已被猎人 " + ChatColor.GOLD + hunter.getName() + ChatColor.RED + " 抓捕！");
            addPoints(hunter.getUniqueId(), pointsPerRunnerCaught);
        } else {
            Bukkit.broadcastMessage(ChatColor.AQUA + runner.getName() + ChatColor.YELLOW + " 已" + catchReason + "！");
        }

        checkGameEndConditions("逃亡者被捕/弃权 (" + runner.getName() + ")");
    }


    public void checkGameEndConditions(String reasonForCheck) {
        if (currentPhase != GamePhase.RUNNING && currentPhase != GamePhase.PREPARATION) return;

        if (getRunners().isEmpty() && !getHunters().isEmpty() && currentPhase == GamePhase.RUNNING) {
            Bukkit.broadcastMessage(ChatColor.RED + "所有逃亡者均已被捕或弃权！" + ChatColor.GOLD + "猎人获胜！");
            endGameCleanup(true, reasonForCheck);
            return;
        }

        if (flagTaskEnabled && flagsSuccessfullyPlacedCount >= requiredFlagsToPlace && currentPhase == GamePhase.RUNNING) {
            String successMsg = getPluginConfig().getString("flag_task.task_success_broadcast", "&6[团队任务] &a逃亡者们成功收集了所有旗帜！");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', successMsg));
            Bukkit.broadcastMessage(ChatColor.GREEN+"逃亡者因完成旗帜任务而获胜！");
            endGameCleanup(true, "旗帜任务完成");
        }
    }

    private String formatTime(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds);
        long seconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void initializePlayerPoints(Player player) {
        playerPoints.put(player.getUniqueId(), 0);
        updatePlayerNameAndTab(player);
    }

    public void addPoints(UUID playerUUID, int pointsToAdd) {
        if (!pointsSystemEnabled && pointsToAdd == 0) return;
        playerPoints.put(playerUUID, playerPoints.getOrDefault(playerUUID, 0) + pointsToAdd);
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            updatePlayerNameAndTab(player);
        }
    }

    public int getPoints(UUID playerUUID) {
        return playerPoints.getOrDefault(playerUUID, 0);
    }

    public void updatePlayerNameAndTab(Player player) {
        if (player == null || !player.isOnline()) return;

        if (!pointsSystemEnabled || currentPhase == GamePhase.WAITING || currentPhase == GamePhase.FINISHED){
            resetPlayerNameAndTab(player);
            return;
        }

        int points = getPoints(player.getUniqueId());
        String format = pointsDisplayFormat;

        String nameToShow = ChatColor.translateAlternateColorCodes('&',
                format.replace("{player}", player.getName()).replace("{points}", String.valueOf(points))
        );

        try {
            if (nameToShow.length() > 48) nameToShow = nameToShow.substring(0, 48);
            player.setDisplayName(nameToShow);

            String listName = nameToShow;
            if (listName.length() > 16 && Bukkit.getBukkitVersion().contains("1.8")) {
                listName = listName.substring(0,16);
            } else if (listName.length() > 40) {
                listName = listName.substring(0,40);
            }
            player.setPlayerListName(listName);

        } catch (IllegalArgumentException e) {
            getLogger().warning("设置玩家 " + player.getName() + " 的显示名称/Tab列表名称时发生错误 (可能过长): " + nameToShow);
            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());
        }
    }

    public void resetPlayerNameAndTab(Player player){
        if (player == null || !player.isOnline()) return;
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
    }

    public void updateAllPlayerNamesAndTabs() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayerNameAndTab(p);
        }
    }

    public void setScoreboardStatus(UUID playerUUID, boolean status) {
        scoreboardStatus.put(playerUUID, status);
        updatePlayerSidebarScoreboard(playerUUID);
    }

    public boolean getScoreboardStatus(UUID playerUUID) {
        return scoreboardStatus.getOrDefault(playerUUID, getPluginConfig().getBoolean("scoreboard.default_on_join", true));
    }

    public void updatePlayerSidebarScoreboard(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.isOnline()) return;

        if (!getScoreboardStatus(playerUUID) || currentPhase == GamePhase.WAITING || currentPhase == GamePhase.FINISHED) {
            if (player.getScoreboard() != null && player.getScoreboard().getObjective("hunterGameSidebar") != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("hunterGameSidebar", "dummy", ChatColor.translateAlternateColorCodes('&', getPluginConfig().getString("scoreboard.title", "&6&l猎人游戏")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int scoreIndex = 15;

        String phaseDisplay = ChatColor.GRAY+"等待中";
        String timeDisplay = "N/A";
        if (currentPhase == GamePhase.PREPARATION) {
            phaseDisplay = ChatColor.AQUA + "准备阶段";
            timeDisplay = formatTime(preparationTimeRemaining);
        } else if (currentPhase == GamePhase.RUNNING) {
            phaseDisplay = ChatColor.GOLD + "逃亡中";
            timeDisplay = formatTime(gameTimeRemaining);
        }
        objective.getScore(ChatColor.WHITE + "阶段: " + phaseDisplay).setScore(scoreIndex--);
        objective.getScore(ChatColor.WHITE + "时间: " + ChatColor.YELLOW + timeDisplay).setScore(scoreIndex--);
        objective.getScore(ChatColor.DARK_GRAY + String.join("", Collections.nCopies(16, "-"))).setScore(scoreIndex--);

        objective.getScore(ChatColor.RED + "猎人: " + ChatColor.WHITE + getHunters().size()).setScore(scoreIndex--);
        objective.getScore(ChatColor.GREEN + "逃亡者: " + ChatColor.WHITE + getRunners().size()).setScore(scoreIndex--);

        String role = ChatColor.GRAY + "旁观者";
        if (isHunter(playerUUID)) {
            role = ChatColor.RED + "猎人";
        } else if (isRunner(playerUUID)) {
            role = ChatColor.GREEN + "逃亡者";
        }
        objective.getScore(ChatColor.BLUE + "你的身份: " + role).setScore(scoreIndex--);

        String npcTaskStatusForPlayer = npcTaskCompletedPlayers.contains(playerUUID) ? ChatColor.GREEN + "你已完成" : ChatColor.YELLOW + "你未完成";
        objective.getScore(ChatColor.GOLD + "NPC任务: " + npcTaskStatusForPlayer).setScore(scoreIndex--);
        objective.getScore(ChatColor.DARK_AQUA + "  总名额: "+ChatColor.WHITE + npcTaskCompletedPlayers.size() + "/" + npcTaskMaxCompletions).setScore(scoreIndex--);


        if (flagTaskEnabled) {
            objective.getScore(ChatColor.DARK_PURPLE + "旗帜进度: " + ChatColor.WHITE + flagsSuccessfullyPlacedCount + "/" + requiredFlagsToPlace).setScore(scoreIndex--);
        }

        objective.getScore(" ").setScore(scoreIndex--);

        List<String> footerLines = getPluginConfig().getStringList("scoreboard.footer");
        if (footerLines != null && !footerLines.isEmpty()) {
            objective.getScore(ChatColor.DARK_GRAY + String.join("", Collections.nCopies(16, "="))).setScore(scoreIndex--);
            for (String line : footerLines) {
                if (scoreIndex < 1) break;
                objective.getScore(ChatColor.translateAlternateColorCodes('&', line)).setScore(scoreIndex--);
            }
        }
        player.setScoreboard(board);
    }

    public void updateAllPlayerSideBarScoreboards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayerSidebarScoreboard(p.getUniqueId());
        }
    }


    public void handlePlayerJoin(Player player) {
        initializePlayerPoints(player);
        scoreboardStatus.putIfAbsent(player.getUniqueId(), getPluginConfig().getBoolean("scoreboard.default_on_join", true));
        playersAwaitingPassword.remove(player.getUniqueId());

        if (currentPhase == GamePhase.RUNNING || currentPhase == GamePhase.PREPARATION) {
            if (!isHunter(player.getUniqueId()) && !isRunner(player.getUniqueId())) {
                player.sendMessage(ChatColor.GRAY + "游戏正在进行中，你现在是旁观者。");
                player.setGameMode(GameMode.SPECTATOR);
                if(spectatorSpawnLocation != null) player.teleport(spectatorSpawnLocation);
            } else {
                if(isHunter(player.getUniqueId())) {
                    Location hunterSpawn = parseLocationFromString(getPluginConfig().getString("spawn_points.hunters"), "spawn_points.hunters");
                    if(hunterSpawn != null) player.teleport(hunterSpawn);
                    if(currentPhase == GamePhase.PREPARATION) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, preparationTimeRemaining * 20 + 40, 255, false, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, preparationTimeRemaining * 20 + 40, 128, false, false, false));
                    } else if (currentPhase == GamePhase.RUNNING && hunterWeapon != null && !player.getInventory().containsAtLeast(hunterWeapon.clone(), 1)) {
                        ItemStack currentHunterWeapon = hunterWeapon.clone();
                        ItemMeta meta = currentHunterWeapon.getItemMeta();
                        if (meta != null) {
                            meta.setUnbreakable(true);
                            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                            currentHunterWeapon.setItemMeta(meta);
                        }
                        player.getInventory().addItem(currentHunterWeapon);
                    }
                } else if (isRunner(player.getUniqueId())) {
                    Location runnerSpawn = parseLocationFromString(getPluginConfig().getString("spawn_points.runners"), "spawn_points.runners");
                    if(runnerSpawn != null) player.teleport(runnerSpawn);
                    boolean hasIdCard = false;
                    for(ItemStack item : player.getInventory().getContents()){
                        if(item != null && item.isSimilar(getIdentityCard())){
                            hasIdCard = true;
                            break;
                        }
                    }
                    if (!hasIdCard) {
                        player.getInventory().addItem(getIdentityCard().clone());
                    }
                }
            }
        }
        updatePlayerNameAndTab(player);
        updatePlayerSidebarScoreboard(player.getUniqueId());
    }

    public void handlePlayerQuit(Player player) {
        if (currentPhase == GamePhase.RUNNING || currentPhase == GamePhase.PREPARATION) {
            boolean wasRunner = isRunner(player.getUniqueId());
            if (wasRunner) {
                getLogger().info("逃亡者 " + player.getName() + " 退出了游戏。");
                handleRunnerCaught(player, null, "退出游戏");
            } else if (isHunter(player.getUniqueId())) {
                getLogger().info("猎人 " + player.getName() + " 退出了游戏。");
            }
        }
        scoreboardStatus.remove(player.getUniqueId());
        playersAwaitingPassword.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;

        Player player = event.getPlayer();
        if (!isRunner(player.getUniqueId())) return;
        if (currentPhase != GamePhase.RUNNING && currentPhase != GamePhase.PREPARATION) return;

        NPC npc = CitizensAPI.getNPCRegistry().getNPC(event.getRightClicked());
        if (npc == null || !npc.data().get(NAMEPLATE_VISIBLE_KEY, true) || !npc.data().get("hunter_quest_npc", false)) {
            return;
        }

        event.setCancelled(true);

        if (npcTaskCompletedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "你已经完成过这个NPC任务了。");
            return;
        }

        if (npcTaskCompletedPlayers.size() >= npcTaskMaxCompletions) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',getPluginConfig().getString("npc_interaction_task.task_slots_full_message", "&eNPC任务名额已满！")));
            applyGlowingToUnfinishedNPCTaskRunners();
            return;
        }

        if (npcPasswordEnabled) {
            playersAwaitingPassword.add(player.getUniqueId());
            player.sendMessage(npcPasswordPrompt);
        } else {
            proceedWithNpcTask(player);
        }
    }

    private void proceedWithNpcTask(Player player) {
        ItemStack idCardTemplate = getIdentityCard();
        boolean cardConsumed = false;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.isSimilar(idCardTemplate)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                cardConsumed = true;
                break;
            }
        }

        if(!cardConsumed){
            player.sendMessage(ChatColor.RED + "你需要一个有效的["+ChatColor.stripColor(idCardTemplate.getItemMeta().getDisplayName())+ChatColor.RED+"]才能与此NPC交互！");
            return;
        }

        npcTaskCompletedPlayers.add(player.getUniqueId());
        addPoints(player.getUniqueId(), pointsPerNpcTask);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', getPluginConfig().getString("npc_interaction_task.task_completion_message", "&a你成功与NPC交互，完成了一项个人任务！身份卡已被消耗。")));
        updateAllPlayerSideBarScoreboards();

        if (npcTaskCompletedPlayers.size() >= npcTaskMaxCompletions) {
            applyGlowingToUnfinishedNPCTaskRunners();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (playersAwaitingPassword.contains(player.getUniqueId())) {
            event.setCancelled(true);
            String enteredPassword = event.getMessage();

            Bukkit.getScheduler().runTask(this, () -> {
                playersAwaitingPassword.remove(player.getUniqueId());
                if (enteredPassword.equals(npcPassword)) {
                    player.sendMessage(npcPasswordCorrect);
                    proceedWithNpcTask(player);
                } else {
                    player.sendMessage(npcPasswordIncorrect);
                }
            });
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!flagTaskEnabled || currentPhase != GamePhase.RUNNING || !isRunner(player.getUniqueId())) {
            return;
        }

        if (block.getType() == bannerMaterialToPickup) {
            event.setDropItems(false);
            player.getInventory().addItem(flagTaskItem.clone());

            String pickupMsg = getPluginConfig().getString("flag_task.flag_pickup_message", "&e你拾取了一面任务旗帜！小心，这可能会吸引猎人！");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', pickupMsg));

            if (bannerPickupSound != null) {
                player.playSound(player.getLocation(), bannerPickupSound, bannerPickupVolume, bannerPickupPitch);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!flagTaskEnabled || currentPhase != GamePhase.RUNNING || !isRunner(player.getUniqueId())) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || !itemInHand.isSimilar(flagTaskItem)) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Location potentialPlaceLocation = clickedBlock.getRelative(event.getBlockFace()).getLocation().getBlock().getLocation();

        boolean isTargetLocation = false;
        for (Location targetLoc : flagTargetPlacementLocations) {
            if (targetLoc.equals(potentialPlaceLocation)) {
                isTargetLocation = true;
                break;
            }
        }

        if (isTargetLocation) {
            event.setCancelled(true);
            if (occupiedFlagTargets.contains(potentialPlaceLocation)) {
                String alreadyPlacedMsg = getPluginConfig().getString("flag_task.flag_already_placed_message", "&c这个目标点已经有旗帜了！");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', alreadyPlacedMsg));
                if (bannerPlaceFailSound != null) {
                    player.playSound(player.getLocation(), bannerPlaceFailSound, bannerPlaceFailVolume, bannerPlaceFailPitch);
                }
                return;
            }

            Block blockToPlace = potentialPlaceLocation.getBlock();
            if (blockToPlace.getType().isAir() || blockToPlace.isPassable()) {
                blockToPlace.setType(bannerMaterialToPickup);

                if (itemInHand.getItemMeta() instanceof BannerMeta && blockToPlace.getState() instanceof Banner) {
                    BannerMeta handMeta = (BannerMeta) itemInHand.getItemMeta();
                    Banner placedBannerState = (Banner) blockToPlace.getState();
                    placedBannerState.setPatterns(handMeta.getPatterns());
                    placedBannerState.update(true);
                }

                occupiedFlagTargets.add(potentialPlaceLocation);
                flagsSuccessfullyPlacedCount++;
                addPoints(player.getUniqueId(), pointsPerFlagPlace);


                if (itemInHand.getAmount() > 1) {
                    itemInHand.setAmount(itemInHand.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }

                String placedMsg = getPluginConfig().getString("flag_task.flag_placed_message", "&a你成功将一面旗帜放置到了目标点！({COUNT}/{TOTAL})")
                        .replace("{COUNT}", String.valueOf(flagsSuccessfullyPlacedCount))
                        .replace("{TOTAL}", String.valueOf(requiredFlagsToPlace));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', placedMsg));
                updateAllPlayerSideBarScoreboards();

                if (bannerPlaceSound != null) {
                    player.playSound(player.getLocation(), bannerPlaceSound, bannerPlaceVolume, bannerPlacePitch);
                }

                checkGameEndConditions("旗帜放置");

            } else {
                player.sendMessage(ChatColor.RED + "无法在此处放置旗帜，目标位置不为空或不可放置。");
                if (bannerPlaceFailSound != null) {
                    player.playSound(player.getLocation(), bannerPlaceFailSound, bannerPlaceFailVolume, bannerPlaceFailPitch);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block blockPlaced = event.getBlockPlaced();
        ItemStack itemInHand = event.getItemInHand();

        if (!flagTaskEnabled || currentPhase != GamePhase.RUNNING || !isRunner(player.getUniqueId())) {
            return;
        }

        if (itemInHand != null && itemInHand.isSimilar(flagTaskItem)) {
            Location placedLocation = blockPlaced.getLocation().getBlock().getLocation();
            boolean isTarget = false;
            for (Location targetLoc : flagTargetPlacementLocations) {
                if (targetLoc.equals(placedLocation)) {
                    isTarget = true;
                    break;
                }
            }
            if (!isTarget) {
                String notTargetMsg = getPluginConfig().getString("flag_task.flag_not_target_location_message", "&c这里不是旗帜的目标放置点！请对准目标点右键。");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', notTargetMsg));
                if (bannerPlaceFailSound != null) {
                    player.playSound(player.getLocation(), bannerPlaceFailSound, bannerPlaceFailVolume, bannerPlaceFailPitch);
                }
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (currentPhase != GamePhase.RUNNING) return;

        Entity victimEntity = event.getEntity();
        Entity damagerEntity = event.getDamager();

        if (!(victimEntity instanceof Player) || !(damagerEntity instanceof Player)) {
            return;
        }

        Player victim = (Player) victimEntity;
        Player attacker = (Player) damagerEntity;

        if (isRunner(victim.getUniqueId()) && isHunter(attacker.getUniqueId())) {
            ItemStack itemInHand = attacker.getInventory().getItemInMainHand();

            ItemStack freshHunterWeapon = hunterWeapon.clone();
            ItemMeta freshMeta = freshHunterWeapon.getItemMeta();
            if (freshMeta != null) {
                freshMeta.setUnbreakable(true);
                freshMeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                freshHunterWeapon.setItemMeta(freshMeta);
            }

            if (freshHunterWeapon != null && itemInHand != null && itemInHand.isSimilar(freshHunterWeapon)) {
                handleRunnerCaught(victim, attacker, "被抓捕");
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        if (currentPhase == GamePhase.RUNNING && isHunter(player.getUniqueId())) {
            ItemStack currentHunterWeapon = hunterWeapon.clone();
            ItemMeta meta = currentHunterWeapon.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                currentHunterWeapon.setItemMeta(meta);
            }
            if (currentHunterWeapon != null && currentHunterWeapon.isSimilar(droppedItem)) {
                event.setCancelled(true);
                player.sendMessage(cannotDropWeaponMessage);
            }
        }
    }
}