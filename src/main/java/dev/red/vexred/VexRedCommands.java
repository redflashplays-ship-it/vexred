package dev.red.vexred;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vexbot.api.VexBotApi;
import vexbot.bot.BotManager;
import vexbot.team.TeamManager;

import java.util.*;

final class VexRedCommands {
    private VexRedCommands() {}

    static int killRandom(ServerCommandSource source, int amount, String exclusionText) {
        return kill(source, amount, null, exclusionText);
    }

    static int killTeamRandom(ServerCommandSource source, int amount, String team, String exclusionText) {
        if (!TeamManager.exists(team)) {
            source.sendError(Text.literal("[VexRed] VexBot team not found: " + team));
            return 0;
        }
        return kill(source, amount, team, exclusionText);
    }

    private static int kill(ServerCommandSource source, int amount, String requiredTeam, String exclusionText) {
        MinecraftServer server = source.getServer();
        String[] exclusions = exclusionText == null || exclusionText.isBlank() ? new String[0] : exclusionText.trim().split("\\s+");
        if (exclusions.length > 4) {
            source.sendError(Text.literal("[VexRed] You can specify at most four exclusions."));
            return 0;
        }
        Set<String> excludedNames = new HashSet<>();
        Set<String> excludedTeams = new HashSet<>();
        for (String exclusion : exclusions) {
            if (TeamManager.exists(exclusion)) excludedTeams.add(exclusion.toLowerCase(Locale.ROOT));
            else excludedNames.add(exclusion.toLowerCase(Locale.ROOT));
        }

        List<String> candidates = new ArrayList<>();
        for (String name : BotManager.getBotNames()) {
            // Safety invariant: only names tracked as VexBot bots are candidates; never iterate all players.
            if (!BotManager.isTrackedBotName(name) || !VexBotApi.isBot(server, name)) continue;
            if (requiredTeam != null && !TeamManager.isMemberOfTeam(name, requiredTeam)) continue;
            if (excludedNames.contains(name.toLowerCase(Locale.ROOT))) continue;
            boolean excludedByTeam = false;
            for (String team : excludedTeams) {
                if (TeamManager.isMemberOfTeam(name, team)) { excludedByTeam = true; break; }
            }
            if (!excludedByTeam) candidates.add(name);
        }
        Collections.shuffle(candidates);
        int killed = 0;
        for (String name : candidates) {
            if (killed >= amount) break;
            // BotManager.kill resolves and removes VexBot bots by their registered name.
            if (BotManager.isTrackedBotName(name) && VexBotApi.isBot(server, name) && BotManager.kill(server, name)) killed++;
        }
        int finalKilled = killed;
        source.sendFeedback(() -> Text.literal("[VexRed] Killed " + finalKilled + " bot(s)."), true);
        return killed;
    }
}
