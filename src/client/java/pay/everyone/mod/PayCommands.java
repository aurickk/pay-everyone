package pay.everyone.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;

public class PayCommands {
    private static final PayManager payManager = PayManager.getInstance();
    
    // Suggestion provider for player names (for exclude command)
    private static final SuggestionProvider<FabricClientCommandSource> PLAYER_SUGGESTIONS = (context, builder) -> {
        List<String> players = payManager.getPlayersForAutocomplete();
        String input = builder.getRemaining();
        
        // Handle space-separated player names - get the last partial name being typed
        String[] parts = input.split("\\s+");
        String lastPart = parts.length > 0 ? parts[parts.length - 1].toLowerCase() : "";
        
        // If input ends with space, we're starting a new name
        if (input.endsWith(" ") || input.isEmpty()) {
            lastPart = "";
        }
        
        // Calculate the start position for suggestions
        int startPos = builder.getStart();
        if (parts.length > 1 || (parts.length == 1 && input.endsWith(" "))) {
            // Find where the last word starts
            int lastSpaceIndex = input.lastIndexOf(' ');
            if (lastSpaceIndex >= 0) {
                startPos = builder.getStart() + lastSpaceIndex + 1;
            }
        }
        
        // Create a new builder at the correct position
        var newBuilder = builder.createOffset(startPos);
        
        for (String player : players) {
            if (player.toLowerCase().startsWith(lastPart) && !payManager.isExcluded(player)) {
                newBuilder.suggest(player);
            }
        }
        return newBuilder.buildFuture();
    };

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // Register /payall command with subcommands
        dispatcher.register(ClientCommandManager.literal("payall")
                // Main payment command: /payall <amount> [delay] (delay defaults to 1000ms)
                .then(ClientCommandManager.argument("amount", StringArgumentType.string())
                        // /payall <amount> - uses default delay of 1000ms
                        .executes(context -> {
                            if (payManager.isPaying()) {
                                context.getSource().sendError(Component.literal("§c[Pay Everyone] Payment process is already in progress! Use '/payall stop' to stop it first."));
                                return 0;
                            }
                            String amountOrRange = StringArgumentType.getString(context, "amount");
                            boolean success = payManager.payAll(amountOrRange, 1000); // Default 1000ms delay
                            if (!success) {
                                context.getSource().sendError(Component.literal("§c[Pay Everyone] Failed to start payment process."));
                                return 0;
                            }
                            return 1;
                        })
                        // /payall <amount> <delay> - custom delay
                        .then(ClientCommandManager.argument("delay", LongArgumentType.longArg(0))
                                .executes(context -> {
                                    if (payManager.isPaying()) {
                                        context.getSource().sendError(Component.literal("§c[Pay Everyone] Payment process is already in progress! Use '/payall stop' to stop it first."));
                                        return 0;
                                    }
                                    String amountOrRange = StringArgumentType.getString(context, "amount");
                                    long delay = LongArgumentType.getLong(context, "delay");
                                    boolean success = payManager.payAll(amountOrRange, delay);
                                    if (!success) {
                                        context.getSource().sendError(Component.literal("§c[Pay Everyone] Failed to start payment process."));
                                        return 0;
                                    }
                                    return 1;
                                })))
                // Subcommand: /payall help - show command guide
                .then(ClientCommandManager.literal("help")
                        .executes(context -> {
                            context.getSource().sendFeedback(Component.literal("§6========== Pay Everyone - Command Guide =========="));
                            context.getSource().sendFeedback(Component.literal(""));
                            
                            context.getSource().sendFeedback(Component.literal("§eMain Commands:"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall <amount> §7- Pay all players (default 1000ms delay)"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall <amount> <delay> §7- Pay with custom delay (ms)"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall stop §7- Stop payment process"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall info §7- Show payment information"));
                            context.getSource().sendFeedback(Component.literal(""));
                            
                            context.getSource().sendFeedback(Component.literal("§eExamples:"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall 100000 §7- Pay 100k to all (1000ms delay)"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall 300-3000 500 §7- Pay random 300-3k (500ms delay)"));
                            context.getSource().sendFeedback(Component.literal(""));
                            
                            context.getSource().sendFeedback(Component.literal("§ePlayer Management:"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall add <players> §7- Add players to payment list"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall exclude <players> §7- Exclude players from payments"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall remove exclude <players> §7- Remove from exclusion list"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall remove add <players> §7- Remove from add list"));
                            context.getSource().sendFeedback(Component.literal(""));
                            
                            context.getSource().sendFeedback(Component.literal("§eClear Commands:"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall clear exclude §7- Clear excluded players"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall clear add §7- Clear manually added players"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall clear tabscan §7- Clear tab scan results"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall clear all §7- Clear all lists"));
                            context.getSource().sendFeedback(Component.literal(""));
                            
                            context.getSource().sendFeedback(Component.literal("§eTab Scan (Discover Players):"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall tabscan §7- Start scan (200ms interval)"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall tabscan <interval> §7- Custom interval (ms)"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall tabscan stop §7- Stop scan in progress"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall tabscan debug §7- Toggle debug mode"));
                            context.getSource().sendFeedback(Component.literal(""));
                            
                            context.getSource().sendFeedback(Component.literal("§eList Commands:"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall list §7- Show debug information"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall list tabscan §7- List tab scan players"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall list add §7- List manually added players"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall list exclude §7- List excluded players"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall list tablist §7- List tab menu players"));
                            context.getSource().sendFeedback(Component.literal(""));
                            
                            context.getSource().sendFeedback(Component.literal("§eAuto-Confirm (for servers with confirmation menus):"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall confirmclickslot §7- Show current status"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall confirmclickslot <slot> §7- Set slot to auto-click"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall confirmclickslot <slot> <delay> §7- Set slot and delay"));
                            context.getSource().sendFeedback(Component.literal("§f  /payall confirmclickslot off §7- Disable auto-confirm"));
                            context.getSource().sendFeedback(Component.literal(""));
                            
                            context.getSource().sendFeedback(Component.literal("§7Tip: Use /payall tabscan to discover players beyond tab list limit"));
                            context.getSource().sendFeedback(Component.literal("§6================================================"));
                            return 1;
                        }))
                // Subcommand: /payall exclude <players>
                .then(ClientCommandManager.literal("exclude")
                        .then(ClientCommandManager.argument("players", StringArgumentType.greedyString())
                                .suggests(PLAYER_SUGGESTIONS)
                                .executes(context -> {
                                    String playersArg = StringArgumentType.getString(context, "players");
                                    String[] players = playersArg.split("\\s+");
                                    
                                    if (players.length == 0 || (players.length == 1 && players[0].isEmpty())) {
                                        context.getSource().sendError(Component.literal("§c[Pay Everyone] Please specify at least one player name"));
                                        return 0;
                                    }

                                    payManager.addExcludedPlayers(players);
                                    context.getSource().sendFeedback(Component.literal(
                                        String.format("§a[Pay Everyone] Added %d player(s) to exclusion list: %s", 
                                            players.length, String.join(", ", players))
                                    ));
                                    return 1;
                                })))
                // Subcommand: /payall clear - with subcommands
                .then(ClientCommandManager.literal("clear")
                        // /payall clear exclude - clear excluded players
                        .then(ClientCommandManager.literal("exclude")
                                .executes(context -> {
                                    payManager.clearExclusions();
                                    context.getSource().sendFeedback(Component.literal("§a[Pay Everyone] Cleared all exclusions"));
                                    return 1;
                                }))
                        // /payall clear add - clear manually added players
                        .then(ClientCommandManager.literal("add")
                                .executes(context -> {
                                    payManager.clearManualPlayers();
                                    context.getSource().sendFeedback(Component.literal("§a[Pay Everyone] Cleared manually added players"));
                                    return 1;
                                }))
                        // /payall clear tabscan - clear tabscan results
                        .then(ClientCommandManager.literal("tabscan")
                                .executes(context -> {
                                    payManager.clearTabScanList();
                                    context.getSource().sendFeedback(Component.literal("§a[Pay Everyone] Cleared tab scan results"));
                                    return 1;
                                }))
                        // /payall clear all - clear everything
                        .then(ClientCommandManager.literal("all")
                                .executes(context -> {
                                    payManager.clearExclusions();
                                    payManager.clearManualPlayers();
                                    payManager.clearTabScanList();
                                    context.getSource().sendFeedback(Component.literal("§a[Pay Everyone] Cleared all lists (exclusions, manual, tabscan)"));
                                    return 1;
                                })))
                // Subcommand: /payall info
                .then(ClientCommandManager.literal("info")
                        .executes(context -> {
                            int onlineCount = payManager.getOnlinePlayerCount();
                            int exclusionCount = payManager.getExclusionCount();
                            int manualCount = payManager.getManualPlayerCount();
                            Set<String> excluded = payManager.getExcludedPlayers();
                            
                            context.getSource().sendFeedback(Component.literal("§6[Pay Everyone] Payment Information:"));
                            context.getSource().sendFeedback(Component.literal(
                                String.format("§e  Total players on pay list: §f%d", onlineCount)
                            ));
                            int tabScanCount = payManager.getTabScanPlayerCount();
                            if (manualCount > 0) {
                                context.getSource().sendFeedback(Component.literal(
                                    String.format("§e  Manual players added: §f%d", manualCount)
                                ));
                            } else if (tabScanCount > 0) {
                                context.getSource().sendFeedback(Component.literal(
                                    String.format("§e  Players from tab scan: §f%d", tabScanCount)
                                ));
                            } else {
                                context.getSource().sendFeedback(Component.literal(
                                    "§7  Note: Running payall without tabscan on large servers may not log all online players."
                                ));
                                context.getSource().sendFeedback(Component.literal(
                                    "§7  Use '/payall tabscan' or '/payall add <players>' for more players."
                                ));
                            }
                            context.getSource().sendFeedback(Component.literal(
                                String.format("§e  Excluded players: §f%d", exclusionCount)
                            ));
                            
                            if (!excluded.isEmpty()) {
                                context.getSource().sendFeedback(Component.literal(
                                    String.format("§e  Excluded: §f%s", String.join(", ", excluded))
                                ));
                            }
                            
                            return 1;
                        }))
                // Subcommand: /payall add <players>
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("players", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String playersArg = StringArgumentType.getString(context, "players");
                                    String[] players = playersArg.split("[, ]+"); // Split by comma or space
                                    
                                    if (players.length == 0 || (players.length == 1 && players[0].isEmpty())) {
                                        context.getSource().sendError(Component.literal("§c[Pay Everyone] Please specify at least one player name"));
                                        return 0;
                                    }

                                    PayManager.AddPlayersResult result = payManager.addManualPlayers(players);
                                    
                                    if (!result.added.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal(
                                            String.format("§a[Pay Everyone] Added %d player(s) to payment list: %s", 
                                                result.added.size(), String.join(", ", result.added))
                                        ));
                                    }
                                    
                                    if (!result.duplicates.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal(
                                            String.format("§e[Pay Everyone] Skipped %d player(s) already on list: %s", 
                                                result.duplicates.size(), String.join(", ", result.duplicates))
                                        ));
                                    }
                                    
                                    if (result.added.isEmpty() && result.duplicates.isEmpty()) {
                                        context.getSource().sendError(Component.literal("§c[Pay Everyone] No valid player names provided"));
                                        return 0;
                                    }
                                    
                                    return 1;
                                })))
                // Subcommand: /payall stop
                .then(ClientCommandManager.literal("stop")
                        .executes(context -> {
                            if (payManager.isPaying()) {
                                payManager.stopPaying();
                                context.getSource().sendFeedback(Component.literal("§c[Pay Everyone] Stopping payment process..."));
                            } else {
                                context.getSource().sendFeedback(Component.literal("§c[Pay Everyone] No payment process is currently running."));
                            }
                            return 1;
                        }))
                // Subcommand: /payall tabscan [interval] - scan players via tab completion
                .then(ClientCommandManager.literal("tabscan")
                        .executes(context -> {
                            if (payManager.isTabScanning()) {
                                context.getSource().sendError(Component.literal("§c[Pay Everyone] Tab scan already in progress! Use '/payall tabscan stop' to stop it."));
                                return 0;
                            }
                            context.getSource().sendFeedback(Component.literal("§6[Pay Everyone] ⚠ WARNING: Please stay still during tab scan! Moving may interfere with the process."));
                            payManager.queryPlayersViaTabComplete(); // Default 200ms
                            return 1;
                        })
                        .then(ClientCommandManager.argument("interval", LongArgumentType.longArg(50, 5000))
                                .executes(context -> {
                                    if (payManager.isTabScanning()) {
                                        context.getSource().sendError(Component.literal("§c[Pay Everyone] Tab scan already in progress! Use '/payall tabscan stop' to stop it."));
                                        return 0;
                                    }
                                    context.getSource().sendFeedback(Component.literal("§6[Pay Everyone] ⚠ WARNING: Please stay still during tab scan! Moving may interfere with the process."));
                                    long interval = LongArgumentType.getLong(context, "interval");
                                    payManager.queryPlayersViaTabComplete(interval);
                                    return 1;
                                }))
                        // /payall tabscan stop - stop tab scan in progress
                        .then(ClientCommandManager.literal("stop")
                                .executes(context -> {
                                    if (payManager.stopTabScan()) {
                                        context.getSource().sendFeedback(Component.literal("§c[Pay Everyone] Tab scan stopped."));
                                    } else {
                                        context.getSource().sendFeedback(Component.literal("§e[Pay Everyone] No tab scan in progress."));
                                    }
                                    return 1;
                                }))
                        // /payall tabscan debug - toggle debug mode for tabscan
                        .then(ClientCommandManager.literal("debug")
                                .executes(context -> {
                                    boolean newState = !payManager.isDebugMode();
                                    payManager.setDebugMode(newState);
                                    context.getSource().sendFeedback(Component.literal(
                                        String.format("§6[Pay Everyone] Tab scan debug: %s", newState ? "§aENABLED" : "§cDISABLED")
                                    ));
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("true")
                                        .executes(context -> {
                                            payManager.setDebugMode(true);
                                            context.getSource().sendFeedback(Component.literal("§6[Pay Everyone] Tab scan debug: §aENABLED"));
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("false")
                                        .executes(context -> {
                                            payManager.setDebugMode(false);
                                            context.getSource().sendFeedback(Component.literal("§6[Pay Everyone] Tab scan debug: §cDISABLED"));
                                            return 1;
                                        }))))
                // Subcommand: /payall remove - remove players from lists
                .then(ClientCommandManager.literal("remove")
                        // /payall remove exclude <players> - remove from exclusion list
                        .then(ClientCommandManager.literal("exclude")
                                .then(ClientCommandManager.argument("players", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String playersArg = StringArgumentType.getString(context, "players");
                                            String[] players = playersArg.split("\\s+");
                                            
                                            if (players.length == 0 || (players.length == 1 && players[0].isEmpty())) {
                                                context.getSource().sendError(Component.literal("§c[Pay Everyone] Please specify at least one player name"));
                                                return 0;
                                            }

                                            List<String> removed = payManager.removeExcludedPlayers(players);
                                            if (removed.isEmpty()) {
                                                context.getSource().sendFeedback(Component.literal(
                                                    "§e[Pay Everyone] None of the specified players were in the exclusion list"
                                                ));
                                            } else {
                                                context.getSource().sendFeedback(Component.literal(
                                                    String.format("§a[Pay Everyone] Removed %d player(s) from exclusion list: %s", 
                                                        removed.size(), String.join(", ", removed))
                                                ));
                                            }
                                            return 1;
                                        })))
                        // /payall remove add <players> - remove from manual add list
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("players", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String playersArg = StringArgumentType.getString(context, "players");
                                            String[] players = playersArg.split("\\s+");
                                            
                                            if (players.length == 0 || (players.length == 1 && players[0].isEmpty())) {
                                                context.getSource().sendError(Component.literal("§c[Pay Everyone] Please specify at least one player name"));
                                                return 0;
                                            }

                                            List<String> removed = payManager.removeManualPlayers(players);
                                            if (removed.isEmpty()) {
                                                context.getSource().sendFeedback(Component.literal(
                                                    "§e[Pay Everyone] None of the specified players were in the add list"
                                                ));
                                            } else {
                                                context.getSource().sendFeedback(Component.literal(
                                                    String.format("§a[Pay Everyone] Removed %d player(s) from add list: %s", 
                                                        removed.size(), String.join(", ", removed))
                                                ));
                                            }
                                            return 1;
                                        }))))
                // Subcommand: /payall list - list players for debugging
                .then(ClientCommandManager.literal("list")
                        .executes(context -> {
                            // Show debug info
                            String debugInfo = payManager.getDebugPlayerLists();
                            for (String line : debugInfo.split("\n")) {
                                context.getSource().sendFeedback(Component.literal(line));
                            }
                            return 1;
                        })
                        // /payall list tabscan - list tabscan players
                        .then(ClientCommandManager.literal("tabscan")
                                .executes(context -> {
                                    List<String> players = payManager.getPlayerListSample("tabscan", 50);
                                    context.getSource().sendFeedback(Component.literal("§6[Pay Everyone] Tab Scan Players (showing up to 50):"));
                                    if (players.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal("§7  No players in tab scan list. Run '/payall tabscan' first."));
                                    } else {
                                        context.getSource().sendFeedback(Component.literal("§f  " + String.join(", ", players)));
                                    }
                                    return 1;
                                }))
                        // /payall list add - list manually added players
                        .then(ClientCommandManager.literal("add")
                                .executes(context -> {
                                    List<String> players = payManager.getPlayerListSample("add", 50);
                                    context.getSource().sendFeedback(Component.literal("§6[Pay Everyone] Manually Added Players (showing up to 50):"));
                                    if (players.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal("§7  No manually added players. Use '/payall add <players>' to add."));
                                    } else {
                                        context.getSource().sendFeedback(Component.literal("§f  " + String.join(", ", players)));
                                    }
                                    return 1;
                                }))
                        // /payall list exclude - list excluded players
                        .then(ClientCommandManager.literal("exclude")
                                .executes(context -> {
                                    List<String> players = payManager.getPlayerListSample("exclude", 50);
                                    context.getSource().sendFeedback(Component.literal("§6[Pay Everyone] Excluded Players (showing up to 50):"));
                                    if (players.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal("§7  No excluded players."));
                                    } else {
                                        context.getSource().sendFeedback(Component.literal("§f  " + String.join(", ", players)));
                                    }
                                    return 1;
                                }))
                        // /payall list tablist - list tab list players
                        .then(ClientCommandManager.literal("tablist")
                                .executes(context -> {
                                    List<String> players = payManager.getPlayerListSample("tablist", 50);
                                    context.getSource().sendFeedback(Component.literal("§6[Pay Everyone] Tab List Players (showing up to 50):"));
                                    if (players.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal("§7  No players in tab list."));
                                    } else {
                                        context.getSource().sendFeedback(Component.literal("§f  " + String.join(", ", players)));
                                    }
                                    return 1;
                                })))
                // Subcommand: /payall confirmclickslot - auto-click confirmation menu slot
                .then(ClientCommandManager.literal("confirmclickslot")
                        // Show current status when no argument
                        .executes(context -> {
                            int currentSlot = payManager.getConfirmClickSlot();
                            if (currentSlot < 0) {
                                context.getSource().sendFeedback(Component.literal("§6[Pay Everyone] Auto-confirm: §cDISABLED"));
                                context.getSource().sendFeedback(Component.literal("§7  Use '/payall confirmclickslot <slotid>' to enable"));
                            } else {
                                context.getSource().sendFeedback(Component.literal(
                                    String.format("§6[Pay Everyone] Auto-confirm: §aENABLED §7(slot %d, %dms delay)", 
                                        currentSlot, payManager.getConfirmClickDelay())
                                ));
                                context.getSource().sendFeedback(Component.literal("§7  Use '/payall confirmclickslot off' to disable"));
                            }
                            return 1;
                        })
                        // /payall confirmclickslot off - disable auto-confirm
                        .then(ClientCommandManager.literal("off")
                                .executes(context -> {
                                    payManager.setConfirmClickSlot(-1);
                                    context.getSource().sendFeedback(Component.literal("§a[Pay Everyone] Auto-confirm disabled"));
                                    return 1;
                                }))
                        // /payall confirmclickslot <slotid> - set slot to click
                        .then(ClientCommandManager.argument("slotid", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    int slotId = IntegerArgumentType.getInteger(context, "slotid");
                                    payManager.setConfirmClickSlot(slotId);
                                    context.getSource().sendFeedback(Component.literal(
                                        String.format("§a[Pay Everyone] Auto-confirm enabled! Will click slot %d on confirmation menus.", slotId)
                                    ));
                                    context.getSource().sendFeedback(Component.literal(
                                        "§6⚠ WARNING: Stay still while payments run - menus will auto-close!"
                                    ));
                                    return 1;
                                })
                                // /payall confirmclickslot <slotid> <delay> - set slot and delay
                                .then(ClientCommandManager.argument("delay", LongArgumentType.longArg(50, 2000))
                                        .executes(context -> {
                                            int slotId = IntegerArgumentType.getInteger(context, "slotid");
                                            long delay = LongArgumentType.getLong(context, "delay");
                                            payManager.setConfirmClickSlot(slotId);
                                            payManager.setConfirmClickDelay(delay);
                                            context.getSource().sendFeedback(Component.literal(
                                                String.format("§a[Pay Everyone] Auto-confirm enabled! Slot: %d, Delay: %dms", slotId, delay)
                                            ));
                                            context.getSource().sendFeedback(Component.literal(
                                                "§6⚠ WARNING: Stay still while payments run - menus will auto-close!"
                                            ));
                                            return 1;
                                        })))));
    }
}
