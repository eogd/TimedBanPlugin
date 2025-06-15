package eogd;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TimedBanPlugin extends JavaPlugin {

    private static TimedBanPlugin instance;
    private BukkitAudiences adventure;
    private BanDataManager banDataManager;
    private MiniMessage miniMessage;

    public static final String BUNGEE_CHANNEL = "BungeeCord";
    private final Set<UUID> playersToRedirectOnJoin = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        instance = this;
        this.adventure = BukkitAudiences.create(this);
        this.miniMessage = MiniMessage.miniMessage();

        saveDefaultConfig();

        this.banDataManager = new BanDataManager(this);
        this.banDataManager.loadBans();

        getCommand("bantime").setExecutor(new BanTimeCommand(this));
        getCommand("timeunban").setExecutor(new TimeUnbanCommand(this));
        getServer().getPluginManager().registerEvents(new PlayerLoginListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerActivityListener(this), this); // 注册新的监听器

        getServer().getMessenger().registerOutgoingPluginChannel(this, BUNGEE_CHANNEL);

        new BukkitRunnable() {
            @Override
            public void run() {
                banDataManager.cleanupExpiredBans();
            }
        }.runTaskTimerAsynchronously(this, 20L * 60 * 5, 20L * 60 * 5);

        getLogger().info("定时封禁插件 TimedBanPlugin 已启用!");
    }

    @Override
    public void onDisable() {
        if (this.banDataManager != null) {
            this.banDataManager.saveBans();
        }

        getServer().getMessenger().unregisterOutgoingPluginChannel(this, BUNGEE_CHANNEL);

        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        getLogger().info("定时封禁插件 TimedBanPlugin 已禁用!");
    }

    public static TimedBanPlugin getInstance() {
        return instance;
    }

    public BukkitAudiences adventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when a PAPI hook is not enabled!");
        }
        return this.adventure;
    }

    public BanDataManager getBanDataManager() {
        return banDataManager;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public File getPluginDataFolder() {
        return getDataFolder();
    }

    public void sendPlayerToServer(Player player, String serverName) {
        if (!player.isOnline()) return;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(this, BUNGEE_CHANNEL, out.toByteArray());
    }

    public void markForRedirect(UUID uuid) {
        this.playersToRedirectOnJoin.add(uuid);
    }

    public void unmarkForRedirect(UUID uuid) {
        this.playersToRedirectOnJoin.remove(uuid);
    }

    public boolean isMarkedForRedirect(UUID uuid) {
        return this.playersToRedirectOnJoin.contains(uuid);
    }
}