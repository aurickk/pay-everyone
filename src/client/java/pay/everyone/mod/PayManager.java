package pay.everyone.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PayManager {
    private static final PayManager INSTANCE = new PayManager();
    private final Set<String> excludedPlayers = new HashSet<>();
    private boolean isPaying = false;
    private volatile boolean shouldStop = false;
    private final List<String> tabScanPlayerList = new ArrayList<>(); // Players from tab scan
    private final List<String> manualPlayerList = new ArrayList<>(); // Manually added players
    private volatile boolean isTabScanning = false;
    private volatile int tabScanRequestId = 0; // Track our request ID
    private final Object playerListLock = new Object();
    private volatile boolean debugMode = false; // Debug mode toggle

    private PayManager() {}

    public static PayManager getInstance() {
        return INSTANCE;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
    }

    /**
     * Stop the current tab scan in progress
     */
    public boolean stopTabScan() {
        if (isTabScanning) {
            isTabScanning = false;
            scanCompleted = true;
            return true;
        }
        return false;
    }

    public void addExcludedPlayers(String... players) {
        for (String player : players) {
            excludedPlayers.add(player.toLowerCase());
        }
    }

    /**
     * Adds players to the manual list.
     * @return A result object containing added players and skipped duplicates
     */
    public AddPlayersResult addManualPlayers(String... players) {
        List<String> added = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();

        synchronized (playerListLock) {
            for (String player : players) {
                String cleaned = player.trim();
                if (!cleaned.isEmpty()) {
                    if (manualPlayerList.contains(cleaned)) {
                        duplicates.add(cleaned);
                    } else {
                        manualPlayerList.add(cleaned);
                        added.add(cleaned);
                    }
                }
            }
        }

        return new AddPlayersResult(added, duplicates);
    }

    public static class AddPlayersResult {
        public final List<String> added;
        public final List<String> duplicates;

        public AddPlayersResult(List<String> added, List<String> duplicates) {
            this.added = added;
            this.duplicates = duplicates;
        }
    }

    public void clearManualPlayers() {
        synchronized (playerListLock) {
            manualPlayerList.clear();
        }
    }

    /**
     * Remove specific players from manual list
     * @return List of players that were actually removed
     */
    public List<String> removeManualPlayers(String[] playerNames) {
        List<String> removed = new ArrayList<>();
        synchronized (playerListLock) {
            for (String name : playerNames) {
                String cleanName = name.trim();
                if (!cleanName.isEmpty() && manualPlayerList.remove(cleanName)) {
                    removed.add(cleanName);
                }
            }
        }
        return removed;
    }

    public void setManualPlayerList(List<String> players) {
        synchronized (playerListLock) {
            manualPlayerList.clear();
            manualPlayerList.addAll(players);
        }
    }

    public int getManualPlayerCount() {
        synchronized (playerListLock) {
            return manualPlayerList.size();
        }
    }

    public void clearExclusions() {
        excludedPlayers.clear();
    }

    /**
     * Remove specific players from exclusion list
     * @return List of players that were actually removed
     */
    public List<String> removeExcludedPlayers(String[] playerNames) {
        List<String> removed = new ArrayList<>();
        for (String name : playerNames) {
            String cleanName = name.trim().toLowerCase();
            if (!cleanName.isEmpty() && excludedPlayers.remove(cleanName)) {
                removed.add(name.trim());
            }
        }
        return removed;
    }

    public Set<String> getExcludedPlayers() {
        return new HashSet<>(excludedPlayers);
    }

    public int getExclusionCount() {
        return excludedPlayers.size();
    }

    public void clearTabScanList() {
        synchronized (playerListLock) {
            tabScanPlayerList.clear();
        }
    }

    public int getTabScanPlayerCount() {
        synchronized (playerListLock) {
            return tabScanPlayerList.size();
        }
    }

    // Characters to scan through for tab scan (empty prefix + a-z, 0-9, _)
    private static final String[] SCAN_PREFIXES_SINGLE = {
        "", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "_"
    };
    private volatile int currentScanIndex = 0;
    private volatile boolean scanCompleted = false;
    private volatile long scanInterval = 200; // Default 200ms between requests
    private final Set<Integer> processedRequestIds = Collections.synchronizedSet(new HashSet<>()); // Track processed requests

    /**
     * Query player list via tab completion suggestions for /pay command.
     * Scans through single letter prefixes (a-z, 0-9, _).
     * @param intervalMs Milliseconds between each request (default 200)
     */
    public void queryPlayersViaTabComplete(long intervalMs) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || player.connection == null) {
            return;
        }

        if (isTabScanning) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§c[Pay Everyone] Tab scan already in progress..."
            ), false);
            return;
        }

        scanInterval = intervalMs;

        isTabScanning = true;
        currentScanIndex = 0;
        scanCompleted = false;
        processedRequestIds.clear(); // Clear processed IDs for new scan

        // Clear previous scan results
        synchronized (playerListLock) {
            tabScanPlayerList.clear();
        }

        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
            String.format("§e[Pay Everyone] Starting tab scan (%d prefixes, %dms interval)...",
                SCAN_PREFIXES_SINGLE.length, scanInterval)
        ), false);

        // Start scanning in a separate thread to avoid blocking
        CompletableFuture.runAsync(() -> runSequentialScan());
    }

    /**
     * Query with default interval (200ms)
     */
    public void queryPlayersViaTabComplete() {
        queryPlayersViaTabComplete(200);
    }

    private void runSequentialScan() {
        Minecraft minecraft = Minecraft.getInstance();

        for (int i = 0; i < SCAN_PREFIXES_SINGLE.length && isTabScanning; i++) {
            final int index = i; // Final copy for use in lambda
            currentScanIndex = i;
            String prefix = SCAN_PREFIXES_SINGLE[i];
            int requestId = 10000 + i;

            // Send request on main thread
            minecraft.execute(() -> {
                LocalPlayer player = minecraft.player;
                if (player != null && player.connection != null) {
                    String command = "/pay " + prefix;
                    ServerboundCommandSuggestionPacket packet = new ServerboundCommandSuggestionPacket(requestId, command);
                    player.connection.send(packet);

                    if (debugMode) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            String.format("§8[Debug] [%d/%d] Sent: /pay %s", index + 1, SCAN_PREFIXES_SINGLE.length,
                                prefix.isEmpty() ? "(empty)" : prefix)
                        ), false);
                    }
                }
            });

            // Wait before next request (configurable interval)
            try {
                Thread.sleep(scanInterval);
            } catch (InterruptedException e) {
                break;
            }

            // Show progress every 5 prefixes
            if (i % 5 == 0 || i == SCAN_PREFIXES_SINGLE.length - 1) {
                final int progress = (i + 1) * 100 / SCAN_PREFIXES_SINGLE.length;
                final int currentCount;
                synchronized (playerListLock) {
                    currentCount = tabScanPlayerList.size();
                }
                minecraft.execute(() -> {
                    LocalPlayer player = minecraft.player;
                    if (player != null) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            String.format("§e[Pay Everyone] Scanning... %d%% - Found %d players", progress, currentCount)
                        ), false);
                    }
                });
            }
        }

        // Scan complete
        finishScan();
    }

    private void finishScan() {
        if (scanCompleted) {
            return; // Prevent duplicate completion messages
        }
        scanCompleted = true;
        isTabScanning = false;

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                int totalPlayers;
                synchronized (playerListLock) {
                    totalPlayers = tabScanPlayerList.size();
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    String.format("§a[Pay Everyone] Tab scan complete! Found %d players.", totalPlayers)
                ), false);
            }
        });
    }

    /**
     * Handle tab completion response from server
     */
    public void handleTabCompletionResponse(int requestId, List<String> suggestions) {
        // Only process if this is our tabscan request
        if (!isTabScanning) {
            return;
        }

        // Check if this is one of our request IDs (any ID in our range)
        boolean isOurRequest = (requestId >= 10000 && requestId < 10000 + SCAN_PREFIXES_SINGLE.length);
        if (!isOurRequest) {
            return;
        }

        // Check if we already processed this request (server can send duplicates)
        if (!processedRequestIds.add(requestId)) {
            // Already processed this request ID, ignore duplicate
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        // Get the prefix for this request
        int prefixIndex = requestId - 10000;
        String prefix = "(unknown)";
        if (prefixIndex >= 0 && prefixIndex < SCAN_PREFIXES_SINGLE.length) {
            prefix = SCAN_PREFIXES_SINGLE[prefixIndex].isEmpty() ? "(empty)" : SCAN_PREFIXES_SINGLE[prefixIndex];
        }

        int beforeCount, afterCount, newPlayers;
        synchronized (playerListLock) {
            beforeCount = tabScanPlayerList.size();

            for (String suggestion : suggestions) {
                String cleaned = suggestion.trim();
                // Remove any formatting or extra characters
                cleaned = cleaned.replaceAll("§[0-9a-fk-or]", "");
                if (!cleaned.isEmpty() && !tabScanPlayerList.contains(cleaned)) {
                    tabScanPlayerList.add(cleaned);
                }
            }
            afterCount = tabScanPlayerList.size();
            newPlayers = afterCount - beforeCount;
        }

        // Debug output (only when debug mode is on)
        if (player != null && debugMode) {
            final String finalPrefix = prefix;
            final int finalNew = newPlayers;
            final int finalTotal = afterCount;
            final int finalSuggestions = suggestions.size();
            minecraft.execute(() -> {
                LocalPlayer p = minecraft.player;
                if (p != null) {
                    p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        String.format("§7[Debug] '%s': %d suggestions, +%d new (total: %d)",
                            finalPrefix, finalSuggestions, finalNew, finalTotal)
                    ), false);
                }
            });
        }
    }

    public boolean isTabScanning() {
        return isTabScanning;
    }

    public int getTabScanRequestId() {
        return tabScanRequestId;
    }

    /**
     * Get list of players available for autocomplete (for exclude command)
     * This returns ALL players from all sources, not filtered by exclusions
     */
    public List<String> getPlayersForAutocomplete() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;

        if (localPlayer == null) {
            return Collections.emptyList();
        }

        String localPlayerName = localPlayer.getGameProfile().getName();
        Set<String> allPlayers = new HashSet<>();

        // Add players from tab list
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) {
            Collection<PlayerInfo> playerInfoList = connection.getOnlinePlayers();
            for (PlayerInfo info : playerInfoList) {
                allPlayers.add(info.getProfile().getName());
            }
        }

        // Add players from tabscan
        synchronized (playerListLock) {
            allPlayers.addAll(tabScanPlayerList);
        }

        // Add players from manual list
        synchronized (playerListLock) {
            allPlayers.addAll(manualPlayerList);
        }

        // Remove self
        allPlayers.remove(localPlayerName);

        return new ArrayList<>(allPlayers);
    }

    /**
     * Check if a player is already excluded
     */
    public boolean isExcluded(String playerName) {
        return excludedPlayers.contains(playerName.toLowerCase());
    }

    /**
     * Get all players for debugging - returns raw lists info
     */
    public String getDebugPlayerLists() {
        StringBuilder sb = new StringBuilder();

        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        int tabListCount = connection != null ? connection.getOnlinePlayers().size() : 0;

        synchronized (playerListLock) {
            sb.append("§6=== Pay Everyone Debug Info ===\n");
            sb.append(String.format("§e Tab List Players: §f%d\n", tabListCount));
            sb.append(String.format("§e Tab Scan Players: §f%d\n", tabScanPlayerList.size()));
            sb.append(String.format("§e Manual Players: §f%d\n", manualPlayerList.size()));
            sb.append(String.format("§e Excluded Players: §f%d\n", excludedPlayers.size()));
            sb.append(String.format("§e Total Unique (for payment): §f%d\n", getOnlinePlayers().size()));
        }

        return sb.toString();
    }

    /**
     * Get a sample of players from each list for debugging
     */
    public List<String> getPlayerListSample(String listType, int maxCount) {
        List<String> result = new ArrayList<>();

        switch (listType.toLowerCase()) {
            case "tabscan":
                synchronized (playerListLock) {
                    for (int i = 0; i < Math.min(maxCount, tabScanPlayerList.size()); i++) {
                        result.add(tabScanPlayerList.get(i));
                    }
                }
                break;
            case "manual":
            case "add":
                synchronized (playerListLock) {
                    for (int i = 0; i < Math.min(maxCount, manualPlayerList.size()); i++) {
                        result.add(manualPlayerList.get(i));
                    }
                }
                break;
            case "exclude":
                int count = 0;
                for (String player : excludedPlayers) {
                    if (count++ >= maxCount) break;
                    result.add(player);
                }
                break;
            case "tablist":
                Minecraft minecraft = Minecraft.getInstance();
                ClientPacketListener connection = minecraft.getConnection();
                if (connection != null) {
                    int i = 0;
                    for (PlayerInfo info : connection.getOnlinePlayers()) {
                        if (i++ >= maxCount) break;
                        result.add(info.getProfile().getName());
                    }
                }
                break;
        }

        return result;
    }

    public void handleListCommandResponse(String message) {
        // This method is kept for compatibility but no longer used
        // The /list command approach was unreliable across servers
    }

    public List<String> getOnlinePlayers() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;

        if (localPlayer == null) {
            return Collections.emptyList();
        }

        String localPlayerName = localPlayer.getGameProfile().getName();

        // Priority: manual list > tab scan list > normal tab list
        List<String> sourceList;
        synchronized (playerListLock) {
            if (!manualPlayerList.isEmpty()) {
                // Use manually added players
                sourceList = new ArrayList<>(manualPlayerList);
            } else if (!tabScanPlayerList.isEmpty()) {
                // Use tab scan results
                sourceList = new ArrayList<>(tabScanPlayerList);
            } else {
                // Fallback to tab list (limited to ~80-100 players)
                ClientPacketListener connection = minecraft.getConnection();
                if (connection == null) {
                    return Collections.emptyList();
                }
                Collection<PlayerInfo> playerInfoList = connection.getOnlinePlayers();
                sourceList = playerInfoList.stream()
                        .map(playerInfo -> playerInfo.getProfile().getName())
                        .collect(Collectors.toList());
            }
        }

        return sourceList.stream()
                .filter(name -> !name.equals(localPlayerName)) // Exclude self
                .filter(name -> !excludedPlayers.contains(name.toLowerCase())) // Exclude excluded players
                .collect(Collectors.toList());
    }

    public int getOnlinePlayerCount() {
        return getOnlinePlayers().size();
    }

    public boolean payAll(String amountOrRange, long delayMs) {
        if (isPaying) {
            return false; // Already paying
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null) {
            return false;
        }

        // Note: Client-side mods are limited to what the server sends.
        // The server only sends player info for ~80-100 players (tab list limit).
        // For full server lists, use /payall add or /payall import commands.

        List<String> playersToPay = new ArrayList<>(getOnlinePlayers());

        if (playersToPay.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Pay Everyone] No players to pay!"), false);
            return false;
        }

        // Randomize the order of players to pay
        Collections.shuffle(playersToPay);

        // Parse amount or range
        boolean isRange = amountOrRange.contains("-");
        long parsedMinAmount = 0;
        long parsedMaxAmount = 0;

        if (isRange) {
            String[] parts = amountOrRange.split("-");
            if (parts.length != 2) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Pay Everyone] Invalid range format! Use: min-max"), false);
                return false;
            }
            try {
                parsedMinAmount = Long.parseLong(parts[0].trim());
                parsedMaxAmount = Long.parseLong(parts[1].trim());
                if (parsedMinAmount < 1 || parsedMaxAmount < parsedMinAmount) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Pay Everyone] Invalid range! Min must be >= 1 and max must be >= min"), false);
                    return false;
                }
            } catch (NumberFormatException e) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Pay Everyone] Invalid range format! Use numbers like: 300-3000"), false);
                return false;
            }
        } else {
            try {
                parsedMinAmount = Long.parseLong(amountOrRange.trim());
                parsedMaxAmount = parsedMinAmount;
                if (parsedMinAmount < 1) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Pay Everyone] Amount must be at least 1"), false);
                    return false;
                }
            } catch (NumberFormatException e) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Pay Everyone] Invalid amount format!"), false);
                return false;
            }
        }

        // Make final copies for use in lambda
        final long minAmount = parsedMinAmount;
        final long maxAmount = parsedMaxAmount;
        final boolean finalIsRange = isRange;

        isPaying = true;
        shouldStop = false;

        String amountDisplay = finalIsRange ? String.format("%d-%d", minAmount, maxAmount) : String.valueOf(minAmount);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
            String.format("§a[Pay Everyone] Starting to pay %d players %s money with %dms delay",
                playersToPay.size(), amountDisplay, delayMs)
        ), false);

        Random random = new Random();
        CompletableFuture.runAsync(() -> {
            final int[] paidCount = {0};
            for (int i = 0; i < playersToPay.size(); i++) {
                if (shouldStop) {
                    minecraft.execute(() -> {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            String.format("§c[Pay Everyone] Payment process stopped! Paid %d out of %d players.", paidCount[0], playersToPay.size())
                        ), false);
                    });
                    break;
                }

                String playerName = playersToPay.get(i);

                // Generate random amount if range, otherwise use fixed amount
                long amount = finalIsRange ? (minAmount + random.nextLong(maxAmount - minAmount + 1)) : minAmount;

                String command = String.format("pay %s %d", playerName, amount);

                // Send command to server as a chat command packet
                minecraft.execute(() -> {
                    if (player != null && player.connection != null) {
                        try {
                            // Send as chat command packet to ensure server processes it
                            ServerboundChatCommandPacket packet = new ServerboundChatCommandPacket(command);
                            player.connection.send(packet);
                        } catch (Exception e) {
                            // Fallback to sendCommand if packet creation fails
                            player.connection.sendCommand(command);
                        }
                    }
                });

                paidCount[0]++;

                // Send progress message
                final int currentIndex = i + 1;
                final long finalAmount = amount;
                minecraft.execute(() -> {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        String.format("§e[Pay Everyone] Paying %s %d (%d/%d)", playerName, finalAmount, currentIndex, playersToPay.size())
                    ), false);
                });

                // Wait for delay (except for last player)
                if (i < playersToPay.size() - 1 && !shouldStop) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            // Send completion message
            if (!shouldStop) {
                minecraft.execute(() -> {
                    isPaying = false;
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        String.format("§a[Pay Everyone] Completed paying %d players!", playersToPay.size())
                    ), false);
                });
            } else {
                minecraft.execute(() -> {
                    isPaying = false;
                });
            }
        });

        return true;
    }

    public void stopPaying() {
        if (isPaying && !shouldStop) {
            shouldStop = true;
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player != null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Pay Everyone] Stopping payment process..."), false);
            }
        }
    }

    public boolean isPaying() {
        return isPaying;
    }
}

