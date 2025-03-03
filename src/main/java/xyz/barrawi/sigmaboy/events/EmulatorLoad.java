package xyz.barrawi.sigmaboy.events;


import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.barrawi.sigmaboy.commands.AfkCommand;
import xyz.barrawi.sigmaboy.commands.GOTWCommand;
import xyz.barrawi.sigmaboy.commands.GOTWResetCommand;
import xyz.barrawi.sigmaboy.commands.TOKENCommand;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class EmulatorLoad implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmulatorLoad.class);
    private static final String[] SIGMA_PERMISSIONS = { "cmd_afk", "cmd_gotw", "cmd_token", "cmd_gotw_reset" };

    @EventHandler
    public static void onEmulatorLoaded(EmulatorLoadedEvent event) throws IOException {
        try {
            initialize();

            LOGGER.info("SigmaBoy plugin loaded");
        } catch (Exception ex) {
            LOGGER.error("Error while handling EmulatorLoadedEvent", ex);
        }
    }

    private static void initialize() {
        loadConfig();
        registerAfkCommand();
        registerGOTWCommand();
        registerGOTWResetCommand();
        loadPlayerCommands();
        registerTOKENCommand();
        checkAndUpdatePermissions();
    }

    private static void loadPlayerCommands() {
        try {
            CommandHandler.addCommand(new AfkCommand("cmd_afk", Emulator.getTexts().getValue("cmd.keys.cmd_afk").split(";")));
            CommandHandler.addCommand(new GOTWCommand("cmd_gotw", Emulator.getTexts().getValue("cmd.keys.cmd_gotw").split(";")));
            CommandHandler.addCommand(new TOKENCommand("cmd_token", Emulator.getTexts().getValue("cmd.keys.cmd_token").split(";")));
            CommandHandler.addCommand(new GOTWResetCommand("cmd_gotw_reset", Emulator.getTexts().getValue("cmd.keys.cmd_gotw_reset").split(";")));
        } catch (Exception ex) {
            LOGGER.error("Error loading player commands", ex);
        }
    }

    private static void registerGOTWResetCommand(){
        Emulator.getTexts().register("commands.description.cmd_gotw_reset", ":gotw_reset [-dryrun / -alert] - Reset GOTW");
        Emulator.getTexts().register("cmd.keys.cmd_gotw_reset", "gotw_reset");
        Emulator.getTexts().register("gotw_reset.alert_message", "The Game of the Week Points has been reset for all users!");
    }

    private static void registerAfkCommand() {
        Emulator.getTexts().register("commands.description.cmd_afk", ":afk - toggle AFK status");
        Emulator.getTexts().register("cmd.keys.cmd_afk", "afk;brb");
        Emulator.getTexts().register("sigmaboy.cmd_afk.afk", "* %username% is now AFK! *");
        Emulator.getTexts().register("sigmaboy.cmd_afk.back", "* %username% is now back! *");
        Emulator.getTexts().register("sigmaboy.cmd_afk.time", "* %username% has now been away for %time% minutes *");
    }

    private static void registerGOTWCommand(){
        Emulator.getTexts().register("commands.description.cmd_gotw", ":gotw [user] [amt](admins only) - give gotw points to the user");
        Emulator.getTexts().register("cmd.keys.cmd_gotw", "gotw");
        Emulator.getTexts().register("sigmaboy.cmd_gotw_bad_syntax", "Invalid syntax! Use :gotw username [amount]");
        Emulator.getTexts().register("sigmaboy.cmd_gotw_user_not_found", "User %user% was not found or is offline!");
        Emulator.getTexts().register("sigmaboy.cmd_gotw_bad_integer", "Please provide a valid positive number!");
        Emulator.getTexts().register("sigmaboy.cmd_gotw_points_given", "Successfully gave %points% GOTW points to %user%!");
        Emulator.getTexts().register("sigmaboy.cmd_gotw_error", "An error occurred while executing the GOTW command!");
        Emulator.getTexts().register("sigmaboy.cmd_gotw_success", "You received %points% GOTW points!");
        Emulator.getTexts().register("sigmaboy.cmd_gotw_cooldown", "You must wait %time% seconds before using this command again!");
    }

    private static void registerTOKENCommand(){
        Emulator.getTexts().register("commands.description.cmd_token", ":token [user] [amt](admins only) - give token points to the user");
        Emulator.getTexts().register("cmd.keys.cmd_token", "token");
        Emulator.getTexts().register("sigmaboy.cmd_token_bad_syntax", "Invalid syntax! Use :token username [amount]");
        Emulator.getTexts().register("sigmaboy.cmd_token_user_not_found", "User %user% was not found or is offline!");
        Emulator.getTexts().register("sigmaboy.cmd_token_bad_integer", "Please provide a valid positive number!");
        Emulator.getTexts().register("sigmaboy.cmd_token_points_given", "Successfully gave %points% token points to %user%!");
        Emulator.getTexts().register("sigmaboy.cmd_token_error", "An error occurred while executing the token command!");
        Emulator.getTexts().register("sigmaboy.cmd_token_success", "You received %points% token points!");
        Emulator.getTexts().register("sigmaboy.cmd_token_cooldown", "You must wait %time% seconds before using this command again!");
    }

    private static void loadConfig() {
        Emulator.getConfig().register("sigmaboy.afk_effect_id", "565");
        Emulator.getConfig().register("sigmaboy.afk_update_interval_seconds", "2");
        Emulator.getConfig().register("sigmaboy.afk_status_update_interval_seconds", "60");

        Emulator.getConfig().register("sigmaboy.gotw_min_timer", "900");
        Emulator.getConfig().register("sigmaboy.gotw_bypass_timer_rank", "7");
        Emulator.getConfig().register("sigmaboy.gotw_point_type", "103");
        Emulator.getConfig().register("sigmaboy.gotw_point_amt", "1");

        Emulator.getConfig().register("sigmaboy.token_min_timer", "900");
        Emulator.getConfig().register("sigmaboy.token_bypass_timer_rank", "7");
        Emulator.getConfig().register("sigmaboy.token_point_type", "104");
        Emulator.getConfig().register("sigmaboy.token_point_amt", "1");
    }

    private static void checkAndUpdatePermissions() {
        boolean reloadPermissions = false;
        for (String permission : SIGMA_PERMISSIONS) {
            reloadPermissions |= registerPermission(permission);
        }

        if (reloadPermissions) {
            Emulator.getGameEnvironment().getPermissionsManager().reload();
        }
    }

    private static boolean registerPermission(String name) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement checkStatement = connection.prepareStatement(
                    "SELECT `column_name` FROM INFORMATION_SCHEMA.COLUMNS WHERE `table_name` = 'permissions' AND `column_name` = ?")) {

                checkStatement.setString(1, name);
                if (!checkStatement.executeQuery().next()) {
                    try (PreparedStatement addColumn = connection.prepareStatement(
                            "ALTER TABLE `permissions` ADD `" + name + "` ENUM('0', '1', '2') NOT NULL DEFAULT '0'")) {
                        addColumn.execute();
                        return true;
                    }
                }
                return true;
            }
        } catch (SQLException sql) {
            LOGGER.error("Database permission registration error", sql);
        }
        return false;
    }
}