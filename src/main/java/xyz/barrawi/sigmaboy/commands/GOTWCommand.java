package xyz.barrawi.sigmaboy.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;

public class GOTWCommand extends Command {
    // Configuration keys
    private static final String CONFIG_TIMER_INTERVAL = "sigmaboy.gotw_min_timer";
    private static final String CONFIG_BYPASS_RANK = "sigmaboy.gotw_bypass_timer_rank";
    private static final String CONFIG_POINT_TYPE = "sigmaboy.gotw_point_type";
    private static final String CONFIG_POINT_AMT = "sigmaboy.gotw_point_amt";
    private static final int MAX_POINTS = 31536000; // 1 year in seconds

    // Cache key for cooldown
    private static final String GOTW_COOLDOWN_KEY = "gotw_last_used";

    // Message keys
    private static final class Messages {
        private static final String BAD_SYNTAX = "sigmaboy.cmd_gotw_bad_syntax";
        private static final String USER_NOT_FOUND = "sigmaboy.cmd_gotw_user_not_found";
        private static final String BAD_INTEGER = "sigmaboy.cmd_gotw_bad_integer";
        private static final String POINTS_GIVEN = "sigmaboy.cmd_gotw_points_given";
        private static final String GOTW_ERROR = "sigmaboy.cmd_gotw_error";
        private static final String GOTW_SUCCESS = "sigmaboy.cmd_gotw_success";
        private static final String COOLDOWN_ACTIVE = "sigmaboy.cmd_gotw_cooldown";
    }

    public GOTWCommand(String permission, String[] keys) {
        super(permission, keys);
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        try {
            if (params.length < 2) {
                sendWhisper(gameClient, Messages.BAD_SYNTAX);
                return true;
            }

            Habbo sender = gameClient.getHabbo();
            int senderRank = sender.getHabboInfo().getRank().getId();
            int bypassRank = Emulator.getConfig().getInt(CONFIG_BYPASS_RANK, 7);

            // Check cooldown for non-bypass users
            if (senderRank < bypassRank && !canUseCommand(sender)) {
                int remainingTime = getRemainingCooldown(sender);
                sendWhisper(gameClient, Messages.COOLDOWN_ACTIVE, "time", String.valueOf(remainingTime));
                return true;
            }

            // Find target user
            Habbo targetUser = findTargetUser(gameClient, params[1]);
            if (targetUser == null) {
                return true;
            }

            // Determine points amount
            int points = determinePointsAmount(gameClient, params, senderRank, bypassRank);
            if (points == -1) {
                return true;
            }

            // Execute GOTW award
            executeGOTW(gameClient, targetUser, points);

            // Set cooldown for non-bypass users
            if (senderRank < bypassRank) {
                setUserCooldown(sender);
            }

            return true;
        } catch (Exception e) {
            Emulator.getLogging().logErrorLine(e);
            sendWhisper(gameClient, Messages.GOTW_ERROR);
            return false;
        }
    }

    private boolean canUseCommand(Habbo habbo) {
        Object lastUsedObj = habbo.getHabboStats().cache.get(GOTW_COOLDOWN_KEY);
        if (lastUsedObj == null) {
            return true;
        }

        int lastUsed = (int) lastUsedObj;
        int currentTime = Emulator.getIntUnixTimestamp();
        int timerInterval = Emulator.getConfig().getInt(CONFIG_TIMER_INTERVAL, 900);
        return (currentTime - lastUsed) >= timerInterval;
    }

    private int getRemainingCooldown(Habbo habbo) {
        Object lastUsedObj = habbo.getHabboStats().cache.get(GOTW_COOLDOWN_KEY);
        if (lastUsedObj == null) {
            return 0;
        }

        int lastUsed = (int) lastUsedObj;
        int currentTime = Emulator.getIntUnixTimestamp();
        int timerInterval = Emulator.getConfig().getInt(CONFIG_TIMER_INTERVAL, 900);
        int remainingTime = timerInterval - (currentTime - lastUsed);
        return Math.max(0, remainingTime);
    }

    private void setUserCooldown(Habbo habbo) {
        habbo.getHabboStats().cache.put(GOTW_COOLDOWN_KEY, Emulator.getIntUnixTimestamp());
    }

    private int determinePointsAmount(GameClient gameClient, String[] params, int senderRank, int bypassRank) {
        // For non-bypass ranks, always use default amount
        if (senderRank < bypassRank) {
            return Emulator.getConfig().getInt(CONFIG_POINT_AMT, 1);
        }

        // For bypass ranks, parse custom amount if provided
        if (params.length >= 3) {
            return parsePositiveInteger(gameClient, params[2]);
        }

        return Emulator.getConfig().getInt(CONFIG_POINT_AMT, 1);
    }

    private void executeGOTW(GameClient gc, Habbo targetUser, int points) {
        try {
            int pointType = Emulator.getConfig().getInt(CONFIG_POINT_TYPE, 104);
            targetUser.givePoints(pointType, points);

            // Notify sender
            sendWhisper(gc, Messages.POINTS_GIVEN,
                    "user", targetUser.getHabboInfo().getUsername(),
                    "points", String.valueOf(points));

            // Notify recipient
            sendWhisper(targetUser.getClient(), Messages.GOTW_SUCCESS,
                    "points", String.valueOf(points));
        } catch (Exception e) {
            Emulator.getLogging().logErrorLine(e);
            sendWhisper(gc, Messages.GOTW_ERROR);
        }
    }

    private int parsePositiveInteger(GameClient gc, String value) {
        try {
            int parsedValue = Integer.parseInt(value);
            if (parsedValue <= 0 || parsedValue > MAX_POINTS) {
                throw new NumberFormatException();
            }
            return parsedValue;
        } catch (NumberFormatException e) {
            sendWhisper(gc, Messages.BAD_INTEGER);
            return -1;
        }
    }

    private Habbo findTargetUser(GameClient gc, String username) {
        Habbo user = Emulator.getGameServer().getGameClientManager().getHabbo(username);
        if (user == null || !user.isOnline()) {
            sendWhisper(gc, Messages.USER_NOT_FOUND, "user", username);
            return null;
        }
        return user;
    }

    private void sendWhisper(GameClient gc, String messageKey, String... replacements) {
        String message = Emulator.getTexts().getValue(messageKey);
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        gc.getHabbo().whisper(message, RoomChatMessageBubbles.ALERT);
    }
}