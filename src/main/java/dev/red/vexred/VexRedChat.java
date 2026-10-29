package dev.red.vexred;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import vexbot.api.VexBotApi;
import vexbot.bot.BotManager;
import vexbot.team.TeamManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class VexRedChat {
    static volatile boolean randomChat = true;
    static volatile boolean useSigns = false;
    private static final Random RNG = Random.create();
    private static int tick;
    private static int nextChatTick = 60;
    private static final Map<UUID, PendingConversation> PLAYER_CONVERSATIONS = new ConcurrentHashMap<>();
    private static final Map<String, PendingBotReply> BOT_CONVERSATIONS = new HashMap<>();
    private static final Set<String> recentOpeners = new HashSet<>();

    private record PendingConversation(String botName, String topic, long expiresAt, int turns) {}
    private record PendingBotReply(String speaker, String listener, String topic, long dueTick, int turns) {}
    private record Dialogue(String topic, String line, String[] expected) {}

    private static final Map<String, List<String>> LINES = new LinkedHashMap<>();
    static {
        LINES.put("greeting", List.of(
            "hey!", "yo, what's up?", "hello there.", "oh, hey.", "how's it going?", "good to see someone around.",
            "hey, you doing alright?", "sup?", "morning!", "evening.", "finally, someone else.", "hi! got a second?",
            "hey, haven't seen you around before.", "oh hey, I know you.", "what's good?", "hello hello.", "you look familiar.",
            "hey! how's your day been?", "nice weather for wandering, I guess.", "hey, don't sneak up on me like that."
        ));
        LINES.put("activity", List.of(
            "just walking around, honestly.", "not much, just looking around.", "I'm taking a little break.", "trying to find something interesting.",
            "checking the area out.", "mostly just wandering.", "I was about to head somewhere else.", "killing time, I guess.",
            "looking for a good spot to hang out.", "making sure everything's alright here.", "I lost track of what I was doing.",
            "just stretching my legs.", "I was thinking about exploring a bit.", "not sure yet. what about you?", "I'm keeping an eye on things."
        ));
        LINES.put("question", List.of(
            "what do you think?", "any idea what's going on?", "have you been here long?", "do you know this area?",
            "got any plans?", "where are you headed?", "you seen anything unusual?", "mind if I ask you something?",
            "what's your take on this place?", "you know a good place to go?", "think we should move on?", "what are you up to today?"
        ));
        LINES.put("food", List.of(
            "I'm kind of hungry, not gonna lie.", "if I had food, I'd take a break.", "what's your favorite food?", "I'd go for something warm right now.",
            "I could really go for bread right about now.", "ever notice how good a golden carrot sounds?", "I should probably find something to eat."
        ));
        LINES.put("weather", List.of(
            "the weather's not too bad today.", "this rain is getting old.", "kind of a gloomy day, huh?", "I like it when it's clear out.",
            "hope it doesn't start storming.", "the sky looks weird today.", "at least it's not snowing.", "this is decent wandering weather."
        ));
        LINES.put("joke", List.of(
            "I tried to tell a creeper a joke. it blew up in my face.", "why do I keep losing my stuff? inventory management.",
            "I would tell you a mining joke, but it needs work.", "I had a great joke about stone, but it was pretty rocky.",
            "my plans are like gravel. they keep falling apart.", "I don't trust stairs. they're always up to something.",
            "I used to be afraid of the Nether. then I got over it.", "I had a joke about redstone, but the timing circuit failed."
        ));
        LINES.put("explore", List.of(
            "want to explore a little?", "I wonder what's over there.", "we should see what's beyond this area.", "let's check that place out sometime.",
            "there's probably something interesting nearby.", "I like finding places nobody visits.", "we could go for a short walk.", "ever explore caves much?"
        ));
        LINES.put("fighting", List.of(
            "keep your guard up.", "we're not done here.", "watch yourself.", "you picked the wrong fight.", "back off and nobody needs to get hurt.",
            "I've got my eye on you.", "let's settle this, then.", "don't get careless.", "you wanted a fight. here it is.", "stay out of my way."
        ));
        LINES.put("farewell", List.of(
            "anyway, I'll let you get back to it.", "see you around.", "I'm going to keep moving. later!", "catch you later.",
            "alright, take care.", "I'll see you next time.", "good talking to you.", "I'm off. stay safe."
        ));
    }

    private VexRedChat() {}

    static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(VexRedChat::tick);
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> onPlayerChat(sender, message.getContent().getString()));
    }

    private static void tick(MinecraftServer server) {
        tick++;
        PLAYER_CONVERSATIONS.entrySet().removeIf(e -> e.getValue().expiresAt < tick);
        runBotReplies(server);
        if (!randomChat || tick < nextChatTick) return;
        nextChatTick = tick + 45 + RNG.nextInt(100);
        List<String> names = new ArrayList<>(BotManager.getBotNames());
        Collections.shuffle(names);
        for (String name : names) {
            if (!BotManager.isTrackedBotName(name) || !VexBotApi.isBot(server, name)) continue;
            ServerPlayerEntity bot = VexBotApi.resolveBot(server, name);
            if (bot == null || !bot.isAlive()) continue;
            Nearby nearby = findNearby(server, bot, name);
            if (nearby == null) continue;
            boolean enemy = nearby.botName != null && areEnemies(name, nearby.botName);
            if (enemy && RNG.nextInt(100) >= 7) continue; // hostile teams rarely speak
            String topic = enemy ? "fighting" : chooseTopic();
            String line = randomLine(topic);
            if (useSigns && RNG.nextInt(100) < 35) {
                if (!writeSign(bot, line)) sendNearby(server, bot, name, line);
            } else {
                sendNearby(server, bot, name, line);
            }
            if (nearby.player != null) {
                PLAYER_CONVERSATIONS.put(nearby.player.getUuid(), new PendingConversation(name, topic, tick + 600, 0));
            } else if (nearby.botName != null && !enemy) {
                BOT_CONVERSATIONS.put(nearby.botName.toLowerCase(Locale.ROOT), new PendingBotReply(name, nearby.botName, topic, tick + 20 + RNG.nextInt(40), 0));
            }
            return;
        }
    }

    private record Nearby(ServerPlayerEntity player, String botName) {}

    private static Nearby findNearby(MinecraftServer server, ServerPlayerEntity bot, String botName) {
        ServerPlayerEntity nearestPlayer = null;
        double playerDistance = 8.5 * 8.5;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (VexBotApi.isBot(server, player.getGameProfile().name())) continue;
            if (player.getEntityWorld() != bot.getEntityWorld()) continue;
            double d = player.squaredDistanceTo(bot);
            if (d < playerDistance) { playerDistance = d; nearestPlayer = player; }
        }
        String nearestBot = null;
        double botDistance = 8.5 * 8.5;
        for (String otherName : BotManager.getBotNames()) {
            if (otherName.equalsIgnoreCase(botName) || !BotManager.isTrackedBotName(otherName) || !VexBotApi.isBot(server, otherName)) continue;
            ServerPlayerEntity other = VexBotApi.resolveBot(server, otherName);
            if (other == null || !other.isAlive() || other.getEntityWorld() != bot.getEntityWorld()) continue;
            double d = other.squaredDistanceTo(bot);
            if (d < botDistance) { botDistance = d; nearestBot = otherName; }
        }
        if (nearestPlayer == null && nearestBot == null) return null;
        if (nearestPlayer != null && playerDistance <= botDistance) return new Nearby(nearestPlayer, null);
        return new Nearby(null, nearestBot);
    }

    private static boolean areEnemies(String a, String b) {
        var ta = TeamManager.getTeamForBot(a);
        var tb = TeamManager.getTeamForBot(b);
        if (ta == null || tb == null) return false;
        return !TeamManager.isAlliedWithBot(a, b) && !ta.getName().equalsIgnoreCase(tb.getName());
    }

    private static String chooseTopic() {
        String[] topics = {"greeting", "greeting", "activity", "question", "food", "weather", "joke", "explore", "farewell"};
        return topics[RNG.nextInt(topics.length)];
    }

    private static String randomLine(String topic) {
        List<String> lines = LINES.getOrDefault(topic, LINES.get("activity"));
        return lines.get(RNG.nextInt(lines.size()));
    }

    private static void runBotReplies(MinecraftServer server) {
        Iterator<Map.Entry<String, PendingBotReply>> it = BOT_CONVERSATIONS.entrySet().iterator();
        while (it.hasNext()) {
            PendingBotReply pending = it.next().getValue();
            if (pending.dueTick > tick) continue;
            it.remove();
            ServerPlayerEntity speaker = VexBotApi.resolveBot(server, pending.listener);
            ServerPlayerEntity listener = VexBotApi.resolveBot(server, pending.speaker);
            if (speaker == null || listener == null || !speaker.isAlive() || !listener.isAlive()) continue;
            if (speaker.getEntityWorld() != listener.getEntityWorld() || speaker.squaredDistanceTo(listener) > 8.5 * 8.5) continue;
            if (areEnemies(pending.speaker, pending.listener)) continue;
            String replyTopic = pending.topic.equals("greeting") ? "greeting" : chooseTopic();
            String reply = randomLine(replyTopic);
            if (useSigns && RNG.nextInt(100) < 35) {
                if (!writeSign(speaker, reply)) sendNearby(server, speaker, pending.listener, reply);
            } else sendNearby(server, speaker, pending.listener, reply);
            // A short follow-up makes the exchange feel like a conversation without looping forever.
            if (pending.turns < 1 && RNG.nextInt(100) < 35) {
                BOT_CONVERSATIONS.put(pending.speaker.toLowerCase(Locale.ROOT), new PendingBotReply(pending.listener, pending.speaker, replyTopic, tick + 35 + RNG.nextInt(45), pending.turns + 1));
            }
        }
    }

    private static void onPlayerChat(ServerPlayerEntity player, String raw) {
        PendingConversation pending = PLAYER_CONVERSATIONS.get(player.getUuid());
        if (pending == null || pending.expiresAt < tick || !randomChat) return;
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        ServerPlayerEntity bot = VexBotApi.resolveBot(server, pending.botName);
        if (bot == null || !bot.isAlive() || bot.getEntityWorld() != player.getEntityWorld() || bot.squaredDistanceTo(player) > 8.5 * 8.5) return;
        String topic = classify(raw);
        if (topic == null || !correlates(pending.topic, topic, raw)) return;
        String response = responseFor(topic, raw);
        if (response == null) return;
        if (useSigns && RNG.nextInt(100) < 35) {
            if (!writeSign(bot, response)) sendNearby(server, bot, pending.botName, response);
        } else sendNearby(server, bot, pending.botName, response);
        if (pending.turns >= 2 || topic.equals("farewell")) PLAYER_CONVERSATIONS.remove(player.getUuid());
        else PLAYER_CONVERSATIONS.put(player.getUuid(), new PendingConversation(pending.botName, topic, tick + 500, pending.turns + 1));
    }

    private static String classify(String input) {
        String s = input.toLowerCase(Locale.ROOT);
        if (containsAny(s, "hello", "hi ", "hey", "yo", "sup", "morning", "evening", "hello!", "hi!")) return "greeting";
        if (containsAny(s, "what are you doing", "doing", "nothing much", "walking", "looking around", "taking a break", "not much")) return "activity";
        if (containsAny(s, "food", "hungry", "bread", "eat", "carrot", "steak", "cook")) return "food";
        if (containsAny(s, "weather", "rain", "storm", "snow", "sunny", "cloud", "sky")) return "weather";
        if (containsAny(s, "joke", "funny", "laugh", "lol", "haha", "lmao")) return "joke";
        if (containsAny(s, "explore", "cave", "over there", "adventure", "go somewhere", "let's go", "lets go")) return "explore";
        if (containsAny(s, "fight", "enemy", "attack", "kill", "back off", "weapon", "battle")) return "fighting";
        if (containsAny(s, "where", "why", "how", "what", "who", "when", "plans", "know this area")) return "question";
        if (containsAny(s, "bye", "later", "see you", "goodbye", "gotta go")) return "farewell";
        return null;
    }

    private static boolean correlates(String priorTopic, String newTopic, String raw) {
        // Greetings can be answered by a greeting, and direct answers/questions should stay on-topic.
        if (priorTopic.equals(newTopic)) return true;
        if (priorTopic.equals("question") && Set.of("activity", "explore", "food", "weather", "greeting", "fighting").contains(newTopic)) return true;
        if (priorTopic.equals("activity") && Set.of("activity", "explore", "question", "food").contains(newTopic)) return true;
        if (priorTopic.equals("explore") && Set.of("explore", "question", "activity").contains(newTopic)) return true;
        if (priorTopic.equals("greeting") && newTopic.equals("question") && raw.toLowerCase(Locale.ROOT).contains("how")) return true;
        return priorTopic.equals("farewell") && newTopic.equals("farewell");
    }

    private static String responseFor(String topic, String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        if (topic.equals("greeting")) return new String[]{"hey!", "hello!", "yo!", "hey, what's up?", "hi there!"}[RNG.nextInt(5)];
        if (topic.equals("activity")) return new String[]{"fair enough.", "sounds pretty chill.", "same here, honestly.", "oh, nice.", "gotcha. anything interesting?"}[RNG.nextInt(5)];
        if (topic.equals("food")) return new String[]{"now I'm even hungrier.", "bread is always a solid choice.", "that actually sounds good.", "we should find something to eat.", "what would you pick?"}[RNG.nextInt(5)];
        if (topic.equals("weather")) return new String[]{"yeah, I noticed that too.", "hopefully it clears up.", "weather changes so quickly here.", "I don't mind it too much.", "let's hope it stays that way."}[RNG.nextInt(5)];
        if (topic.equals("joke")) return new String[]{"okay, that got a laugh out of me.", "I've heard worse, honestly.", "your turn to tell one.", "that's terrible. I respect it.", "I'm stealing that one."}[RNG.nextInt(5)];
        if (topic.equals("explore")) return new String[]{"I'm in. where should we go?", "could be fun.", "let's see what's out there.", "sure, as long as we watch our step.", "I was hoping you'd say that."}[RNG.nextInt(5)];
        if (topic.equals("fighting")) return new String[]{"keep your guard up, then.", "let's not start anything unless we have to.", "I'm watching them.", "stay close, just in case.", "we'll handle it."}[RNG.nextInt(5)];
        if (topic.equals("question")) return new String[]{"good question. I'm not totally sure.", "I was wondering the same thing.", "depends on what happens next.", "I can try to find out.", "what do you think?"}[RNG.nextInt(5)];
        if (topic.equals("farewell")) return new String[]{"see you around!", "later, stay safe.", "catch you next time.", "alright, take care.", "bye!"}[RNG.nextInt(5)];
        return null;
    }

    private static boolean containsAny(String s, String... words) {
        for (String word : words) if (s.contains(word)) return true;
        return false;
    }

    private static void sendNearby(MinecraftServer server, ServerPlayerEntity speaker, String speakerName, String line) {
        Text message = Text.literal("<" + speakerName + "> " + line);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() == speaker.getEntityWorld() && player.squaredDistanceTo(speaker) <= 16 * 16) player.sendMessage(message, false);
        }
        // If a bot is speaking to a bot, also ensure nearby players around the recipient see the exchange.
        for (String botName : BotManager.getBotNames()) {
            if (!BotManager.isTrackedBotName(botName) || !VexBotApi.isBot(server, botName)) continue;
            ServerPlayerEntity other = VexBotApi.resolveBot(server, botName);
            if (other == null || other.getEntityWorld() != speaker.getEntityWorld() || other.squaredDistanceTo(speaker) > 8.5 * 8.5) continue;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getEntityWorld() == other.getEntityWorld() && player.squaredDistanceTo(other) <= 16 * 16 && player.squaredDistanceTo(speaker) > 16 * 16) player.sendMessage(message, false);
            }
        }
    }

    private static boolean writeSign(ServerPlayerEntity bot, String message) {
        World world = bot.getEntityWorld();
        BlockPos existing = findExistingBlankSign(world, bot.getBlockPos(), 6);
        if (existing != null) return writeToSign(world, existing, message);
        BlockPos place = findSignSpot(world, bot.getBlockPos());
        if (place == null) return false;
        int rotation = Math.floorMod(Math.round(bot.getYaw() / 22.5f), 16);
        BlockState state = Blocks.OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, rotation);
        if (!world.getBlockState(place).isAir() || !world.setBlockState(place, state, Block.NOTIFY_ALL)) return false;
        return writeToSign(world, place, message);
    }

    private static BlockPos findExistingBlankSign(World world, BlockPos origin, int radius) {
        for (BlockPos pos : BlockPos.iterateOutwards(origin, radius, 2, radius)) {
            if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                SignText front = sign.getFrontText();
                boolean hasBlankLine = false;
                for (int i = 0; i < 4; i++) if (front.getMessage(i, false).getString().isBlank()) { hasBlankLine = true; break; }
                if (hasBlankLine) return pos.toImmutable();
            }
        }
        return null;
    }

    private static BlockPos findSignSpot(World world, BlockPos origin) {
        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                BlockPos pos = origin.add(dx, 0, dz);
                if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) continue;
                if (!world.getBlockState(pos.up()).isAir()) continue;
                return pos;
            }
        }
        return null;
    }

    private static boolean writeToSign(World world, BlockPos pos, String message) {
        if (!(world.getBlockEntity(pos) instanceof SignBlockEntity sign)) return false;
        String[] lines = wrap(message, 4, 15);
        SignText text = sign.getFrontText();
        int nextLine = 0;
        for (int i = 0; i < 4 && nextLine < lines.length; i++) {
            if (text.getMessage(i, false).getString().isBlank() && !lines[nextLine].isBlank()) {
                text = text.withMessage(i, Text.literal(lines[nextLine++]));
            }
        }
        sign.setText(text, true);
        sign.markDirty();
        world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), Block.NOTIFY_ALL);
        return true;
    }

    private static String[] wrap(String text, int maxLines, int maxChars) {
        String[] result = {"", "", "", ""};
        String[] words = text.split("\\s+");
        int line = 0;
        for (String word : words) {
            if (line >= maxLines) break;
            String next = result[line].isEmpty() ? word : result[line] + " " + word;
            if (next.length() <= maxChars) result[line] = next;
            else { line++; if (line < maxLines) result[line] = word.length() > maxChars ? word.substring(0, maxChars) : word; }
        }
        for (int i = 0; i < result.length; i++) if (result[i] == null) result[i] = "";
        return result;
    }
}
