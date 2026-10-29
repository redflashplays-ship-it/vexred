package dev.red.vexred;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class VexRed implements ModInitializer {
    @Override public void onInitialize() {
        VexRedChat.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(literal("vexbot")
                .then(literal("vexred")
                    .then(literal("randomchat").then(literal("on").executes(c -> setChat(c.getSource(), true)))
                        .then(literal("true").executes(c -> setChat(c.getSource(), true)))
                        .then(literal("yes").executes(c -> setChat(c.getSource(), true)))
                        .then(literal("off").executes(c -> setChat(c.getSource(), false)))
                        .then(literal("false").executes(c -> setChat(c.getSource(), false)))
                        .then(literal("no").executes(c -> setChat(c.getSource(), false)))
                        .then(literal("toggle").executes(c -> { VexRedChat.randomChat = !VexRedChat.randomChat; return status(c.getSource(), "Random chat", VexRedChat.randomChat); }))
                        .then(literal("status").executes(c -> status(c.getSource(), "Random chat", VexRedChat.randomChat))))
                    .then(literal("usesigns").then(literal("on").executes(c -> setSigns(c.getSource(), true)))
                        .then(literal("true").executes(c -> setSigns(c.getSource(), true)))
                        .then(literal("yes").executes(c -> setSigns(c.getSource(), true)))
                        .then(literal("off").executes(c -> setSigns(c.getSource(), false)))
                        .then(literal("false").executes(c -> setSigns(c.getSource(), false)))
                        .then(literal("no").executes(c -> setSigns(c.getSource(), false)))
                        .then(literal("toggle").executes(c -> { VexRedChat.useSigns = !VexRedChat.useSigns; return status(c.getSource(), "Sign communication", VexRedChat.useSigns); }))
                        .then(literal("status").executes(c -> status(c.getSource(), "Sign communication", VexRedChat.useSigns))))
                    .then(literal("kill")
                        .then(literal("random").then(argument("amount", IntegerArgumentType.integer(1, 1000))
                            .executes(c -> VexRedCommands.killRandom(c.getSource(), IntegerArgumentType.getInteger(c, "amount"), ""))
                            .then(literal("except").then(argument("exclusions", StringArgumentType.greedyString())
                                .executes(c -> VexRedCommands.killRandom(c.getSource(), IntegerArgumentType.getInteger(c, "amount"), StringArgumentType.getString(c, "exclusions")))))))
                        .then(literal("teamrandom").then(argument("amount", IntegerArgumentType.integer(1, 1000)).then(argument("team", StringArgumentType.word())
                            .executes(c -> VexRedCommands.killTeamRandom(c.getSource(), IntegerArgumentType.getInteger(c, "amount"), StringArgumentType.getString(c, "team"), ""))
                            .then(literal("except").then(argument("exclusions", StringArgumentType.greedyString())
                                .executes(c -> VexRedCommands.killTeamRandom(c.getSource(), IntegerArgumentType.getInteger(c, "amount"), StringArgumentType.getString(c, "team"), StringArgumentType.getString(c, "exclusions"))))))))))));
    }

    private static int setChat(ServerCommandSource s, boolean value) { VexRedChat.randomChat = value; return status(s, "Random chat", value); }
    private static int setSigns(ServerCommandSource s, boolean value) { VexRedChat.useSigns = value; return status(s, "Sign communication", value); }
    private static int status(ServerCommandSource s, String name, boolean value) {
        s.sendFeedback(() -> Text.literal("[VexRed] " + name + ": " + (value ? "ON" : "OFF")), false);
        return 1;
    }
}
