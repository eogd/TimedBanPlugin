package eogd;

import java.util.UUID;

public class CustomBanData {
    private final UUID playerUUID;
    private final long expiryTimestamp;
    private final String originalReason;
    private final String bannerName;
    private final String totalDurationString;
    private final long banTime;
    private int loginAttempts;

    public CustomBanData(UUID playerUUID, long expiryTimestamp, String originalReason, String bannerName, String totalDurationString, long banTime, int loginAttempts) {
        this.playerUUID = playerUUID;
        this.expiryTimestamp = expiryTimestamp;
        this.originalReason = originalReason;
        this.bannerName = bannerName;
        this.totalDurationString = totalDurationString;
        this.banTime = banTime;
        this.loginAttempts = loginAttempts;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public long getExpiryTimestamp() {
        return expiryTimestamp;
    }

    public String getOriginalReason() {
        return originalReason;
    }

    public String getBannerName() {
        return bannerName;
    }

    public String getTotalDurationString() {
        return totalDurationString;
    }

    public long getBanTime() {
        return banTime;
    }

    public int getLoginAttempts() {
        return loginAttempts;
    }

    public void incrementLoginAttempts() {
        this.loginAttempts++;
    }
}