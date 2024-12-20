package xyz.barrawi.sigmaboy.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserTalkComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.users.UserExitRoomEvent;
import com.eu.habbo.plugin.events.users.UserIdleEvent;
import xyz.barrawi.sigmaboy.Main;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AfkCommand extends Command implements EventListener {
    // Configuration keys
    private static final String CONFIG_AFK_EFFECT_ID = "sigmaboy.afk_effect_id";
    private static final String CONFIG_UPDATE_INTERVAL = "sigmaboy.afk_update_interval_seconds";
    private static final String CONFIG_STATUS_UPDATE_INTERVAL = "sigmaboy.afk_status_update_interval_seconds";

    private static final Map<Integer, AfkStatus> afkUsers = new ConcurrentHashMap<>();
    private volatile boolean isRunning;

    public AfkCommand(String permission, String[] keys) {
        super(permission, keys);
        Emulator.getPluginManager().registerEvents(Main.INSTANCE, this);
        startStatusUpdateThread();
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo habbo = gameClient.getHabbo();
        if (!habbo.getRoomUnit().isIdle()) {
            setAfk(habbo);
        }
        return true;
    }

    private static void setAfk(Habbo habbo) {
        if (habbo == null || !habbo.isOnline()) return;

        int userId = habbo.getHabboInfo().getId();
        afkUsers.put(userId, new AfkStatus(Emulator.getIntUnixTimestamp()));

        habbo.getHabboInfo().getCurrentRoom().idle(habbo);
        int effectId = Emulator.getConfig().getInt(CONFIG_AFK_EFFECT_ID, 565);
        habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, effectId, -1);

        String afkMessage = Emulator.getTexts().getValue("sigmaboy.cmd_afk.afk")
                .replace("%username%", habbo.getHabboInfo().getUsername());
        habbo.talk(afkMessage);
    }

    private static void setBack(Habbo habbo) {
        if (habbo == null || !habbo.isOnline()) return;

        int userId = habbo.getHabboInfo().getId();
        if (!afkUsers.containsKey(userId)) return;

        afkUsers.remove(userId);
        habbo.getHabboInfo().getCurrentRoom().unIdle(habbo);
        habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, 0, -1);

        String backMessage = Emulator.getTexts().getValue("sigmaboy.cmd_afk.back")
                .replace("%username%", habbo.getHabboInfo().getUsername());
        habbo.talk(backMessage);
    }

    @EventHandler
    public static void onUserExitRoom(UserExitRoomEvent event) {
        afkUsers.remove(event.habbo.getHabboInfo().getId());
    }

    @EventHandler
    public static void onUserIdle(UserIdleEvent event) {
        if (event.reason == UserIdleEvent.IdleReason.TIMEOUT) {
            setAfk(event.habbo);
        } else if (!event.idle) {
            setBack(event.habbo);
        }
    }

    private void startStatusUpdateThread() {
        if (isRunning) return;

        isRunning = true;
        Emulator.getThreading().run(() -> {
            try {
                while (isRunning) {
                    updateAfkStatuses();
                    int updateInterval = Emulator.getConfig().getInt(CONFIG_UPDATE_INTERVAL, 2);
                    TimeUnit.SECONDS.sleep(updateInterval);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static void updateAfkStatuses() {
        int currentTimestamp = Emulator.getIntUnixTimestamp();
        int statusUpdateInterval = Emulator.getConfig().getInt(CONFIG_STATUS_UPDATE_INTERVAL, 60);

        afkUsers.entrySet().removeIf(entry -> {
            int userId = entry.getKey();
            AfkStatus status = entry.getValue();

            if (currentTimestamp < status.getNextUpdateTime()) {
                return false;
            }

            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (habbo == null || !habbo.isOnline() || !habbo.getRoomUnit().isIdle()) {
                return true;
            }

            sendAfkTimeUpdate(habbo, currentTimestamp - status.getStartTime());
            status.updateNextUpdateTime(currentTimestamp + statusUpdateInterval);
            return false;
        });
    }

    private static void sendAfkTimeUpdate(Habbo habbo, int afkSeconds) {
        String timeMessage = Emulator.getTexts().getValue("sigmaboy.cmd_afk.time")
                .replace("%username%", habbo.getHabboInfo().getUsername())
                .replace("%time%", String.valueOf(afkSeconds / 60));

        RoomChatMessage chatMessage = new RoomChatMessage(
                timeMessage,
                habbo.getRoomUnit(),
                RoomChatMessageBubbles.ALERT
        );

        habbo.getHabboInfo().getCurrentRoom().sendComposer(
                new RoomUserTalkComposer(chatMessage).compose()
        );
    }

    private static class AfkStatus {
        private final int startTime;
        private volatile int nextUpdateTime;

        public AfkStatus(int startTime) {
            this.startTime = startTime;
            int statusUpdateInterval = Emulator.getConfig().getInt(CONFIG_STATUS_UPDATE_INTERVAL, 60);
            this.nextUpdateTime = startTime + statusUpdateInterval;
        }

        public int getStartTime() {
            return startTime;
        }

        public int getNextUpdateTime() {
            return nextUpdateTime;
        }

        public void updateNextUpdateTime(int time) {
            this.nextUpdateTime = time;
        }
    }
}