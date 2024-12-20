package xyz.barrawi.sigmaboy.events;

import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.users.UserExitRoomEvent;
import com.eu.habbo.plugin.events.users.UserIdleEvent;
import xyz.barrawi.sigmaboy.managers.AfkManager;

public class AfkEventListener implements EventListener {
    @EventHandler
    public static void onUserExitRoom(UserExitRoomEvent event) {
        AfkManager.onUserExit(event.habbo);
    }

    @EventHandler
    public static void onUserIdle(UserIdleEvent event) {
        if (event.idle) {
            AfkManager.setAfk(event.habbo);
        } else {
            AfkManager.setBack(event.habbo);
        }
    }
}