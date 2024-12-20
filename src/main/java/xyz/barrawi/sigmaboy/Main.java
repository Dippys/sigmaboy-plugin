package xyz.barrawi.sigmaboy;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.HabboPlugin;
import com.eu.habbo.plugin.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.barrawi.sigmaboy.events.EmulatorLoad;
import xyz.barrawi.sigmaboy.events.AfkEventListener;
import xyz.barrawi.sigmaboy.managers.AfkManager;

public class Main extends HabboPlugin implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static Main INSTANCE = null;

    @Override
    public void onEnable() {
        try {
            INSTANCE = this;
            Emulator.getPluginManager().registerEvents(this, new EmulatorLoad());
            Emulator.getPluginManager().registerEvents(this, new AfkEventListener());
            AfkManager.initialize();
            if (Emulator.isReady) {
                // Additional initialization if needed
            }
            LOGGER.info("SigmaBoy plugin enabled successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to enable SigmaBoy plugin", e);
        }
    }

    @Override
    public void onDisable() {
        try {
            AfkManager.stop();
            LOGGER.info("SigmaBoy plugin disabled successfully");
        } catch (Exception e) {
            LOGGER.error("Error during SigmaBoy plugin disable", e);
        }
    }

    public boolean hasPermission(Habbo habbo, String permission) {
        return false;
    }
}