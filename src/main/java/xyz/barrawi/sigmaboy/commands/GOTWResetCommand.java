package xyz.barrawi.sigmaboy.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.StaffAlertWithLinkComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GOTWResetCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(GOTWResetCommand.class);

    // Configuration keys
    private static final String CONFIG_POINT_TYPE = "sigmaboy.gotw_point_type";

    // Message keys
    private static final class Messages {
        private static final String RESET_SUCCESS = "gotw_reset.success";
        private static final String RESET_ERROR = "gotw_reset.error";
        private static final String ALERT_MESSAGE = "gotw_reset.alert_message";
        private static final String COMMAND_FEEDBACK = "gotw_reset.command_feedback";
        private static final String DRYRUN_FEEDBACK = "gotw_reset.dryrun_feedback";
    }

    // Helper class to store user currency information
    private static class UserCurrency {
        public final String username;
        public final int amount;

        public UserCurrency(String username, int amount) {
            this.username = username;
            this.amount = amount;
        }
    }

    public GOTWResetCommand(String permission, String[] keys) {
        super(permission, keys);
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        try {
            if (gameClient == null) {
                return false;
            }

            // Check command parameters
            boolean sendAlert = hasParameter(params, "-alert");
            boolean dryRun = hasParameter(params, "-dryrun");

            long startTime = System.currentTimeMillis();

            // Get currency type from config
            int currencyType = Emulator.getConfig().getInt(CONFIG_POINT_TYPE, 104);

            // Get top 10 users before reset
            List<UserCurrency> topUsers = getTopUsers(10, currencyType);

            // Get total amount of GOTW points before reset
            int totalPointsBefore = getTotalPoints(currencyType);

            // Process resets based on dry run flag
            int onlineResetCount = 0;
            int offlineResetCount = 0;

            if (!dryRun) {
                // Perform actual reset
                onlineResetCount = resetOnlineUsersCurrency(currencyType);
                offlineResetCount = resetOfflineUsersCurrency(currencyType);

                // Send hotel alert if enabled (only in actual run)
                if (sendAlert) {
                    sendHotelAlert(gameClient, dryRun);
                }
            } else {
                // In dry run, just count how many would be affected
                onlineResetCount = countOnlineUsersWithCurrency(currencyType);
                offlineResetCount = countOfflineUsersWithCurrency(currencyType);
            }

            // Calculate time taken
            long executionTime = System.currentTimeMillis() - startTime;

            // Show detailed feedback to command executor
            showDetailedFeedback(gameClient, topUsers, onlineResetCount,
                    offlineResetCount, totalPointsBefore, executionTime, sendAlert, dryRun);

            // Send simple confirmation in the room
            sendConfirmationWhisper(gameClient, sendAlert, dryRun);

            return true;
        } catch (Exception e) {
            LOGGER.error("[GOTWRESET] Error executing command: {}", e.getMessage(), e);
            sendWhisper(gameClient, Messages.RESET_ERROR);
            return false;
        }
    }

    private boolean hasParameter(String[] params, String param) {
        if (params.length > 1) {
            for (int i = 1; i < params.length; i++) {
                if (params[i].equalsIgnoreCase(param)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the top users with the most GOTW currency
     * @param limit Number of users to fetch
     * @param currencyType The type of currency to check
     * @return List of top users with their currency amounts
     */
    private List<UserCurrency> getTopUsers(int limit, int currencyType) {
        List<UserCurrency> topUsers = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT users.username, users_currency.amount " +
                             "FROM users_currency " +
                             "INNER JOIN users ON users_currency.user_id = users.id " +
                             "WHERE users_currency.type = ? AND users_currency.amount > 0 " +
                             "ORDER BY users_currency.amount DESC " +
                             "LIMIT ?")) {

            statement.setInt(1, currencyType);
            statement.setInt(2, limit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    topUsers.add(new UserCurrency(
                            rs.getString("username"),
                            rs.getInt("amount")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[GOTWRESET] Error getting top GOTW users: {}", e.getMessage(), e);
        }
        return topUsers;
    }

    /**
     * Counts online users who have the specified currency type
     * @param currencyType The type of currency to check
     * @return Number of users with currency
     */
    private int countOnlineUsersWithCurrency(int currencyType) {
        int count = 0;
        for (Map.Entry<Integer, Habbo> entry : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().entrySet()) {
            Habbo habbo = entry.getValue();
            int currentAmount = habbo.getHabboInfo().getCurrencyAmount(currencyType);
            if (currentAmount > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts offline users who have the specified currency type
     * @param currencyType The type of currency to check
     * @return Number of users with currency
     */
    private int countOfflineUsersWithCurrency(int currencyType) {
        int count = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) AS count FROM users_currency WHERE type = ? AND amount > 0")) {

            statement.setInt(1, currencyType);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt("count");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[GOTWRESET] Error counting offline users with GOTW currency: {}", e.getMessage(), e);
        }
        return count;
    }

    /**
     * Resets the GOTW currency for all online users
     * @param currencyType The type of currency to reset
     * @return Number of users affected
     */
    private int resetOnlineUsersCurrency(int currencyType) {
        int count = 0;
        for (Map.Entry<Integer, Habbo> entry : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().entrySet()) {
            Habbo habbo = entry.getValue();
            int currentAmount = habbo.getHabboInfo().getCurrencyAmount(currencyType);
            if (currentAmount > 0) {
                habbo.givePoints(currencyType, -currentAmount);
                count++;
            }
        }
        return count;
    }

    /**
     * Gets the total GOTW points in the system before reset
     * @param currencyType The type of currency to check
     * @return Total points
     */
    private int getTotalPoints(int currencyType) {
        int totalPoints = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT SUM(amount) AS total FROM users_currency WHERE type = ?")) {

            statement.setInt(1, currencyType);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    totalPoints = rs.getInt("total");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[GOTWRESET] Error getting total GOTW points: {}", e.getMessage(), e);
        }
        return totalPoints;
    }

    /**
     * Resets the GOTW currency for offline users via SQL
     * @param currencyType The type of currency to reset
     * @return Number of users affected
     */
    private int resetOfflineUsersCurrency(int currencyType) {
        int affectedRows = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE users_currency SET amount = 0 WHERE type = ? AND amount > 0")) {

            statement.setInt(1, currencyType);
            affectedRows = statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[GOTWRESET] Error resetting offline users GOTW currency: {}", e.getMessage(), e);
        }
        return affectedRows;
    }

    /**
     * Sends a hotel-wide alert
     * @param gameClient The client of the user who executed the command
     * @param isDryRun Whether this is a dry run operation
     */
    private void sendHotelAlert(GameClient gameClient, boolean isDryRun) {
        // Don't send alerts during dry runs
        if (isDryRun) {
            return;
        }

        String alertMessage = Emulator.getTexts().getValue(Messages.ALERT_MESSAGE,
                "The Game of the Week Points has been reset for all users!");

        ServerMessage msg = new StaffAlertWithLinkComposer(
                alertMessage + "\r\n-" + gameClient.getHabbo().getHabboInfo().getUsername(), "").compose();

        for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().entrySet()) {
            Habbo habbo = set.getValue();
            if (habbo.getHabboStats().blockStaffAlerts) {
                continue;
            }
            habbo.getClient().sendResponse(msg);
        }
    }

    /**
     * Shows detailed feedback to the command executor
     */
    private void showDetailedFeedback(GameClient gameClient, List<UserCurrency> topUsers,
                                      int onlineResetCount, int offlineResetCount,
                                      int totalPointsBefore, long executionTime,
                                      boolean alertSent, boolean isDryRun) {
        // Build top users section
        StringBuilder topUsersSection = new StringBuilder();
        topUsersSection.append("<b>Top 10 Users Before Reset</b>\r");

        if (topUsers.isEmpty()) {
            topUsersSection.append("- No users with GOTW points found\r");
        } else {
            int rank = 1;
            for (UserCurrency user : topUsers) {
                topUsersSection.append("- #").append(rank++).append(": ")
                        .append(user.username).append(" (")
                        .append(user.amount).append(" points)\r");
            }
        }

        // Create detailed feedback message
        String headerText = isDryRun ?
                "<b>GOTW Currency Reset Simulation (DRY RUN)</b>\r\n" :
                "<b>GOTW Currency Reset Operation Complete!</b>\r\n";

        String statsText = isDryRun ?
                "<b>Simulation Statistics</b>\r" :
                "<b>Operation Statistics</b>\r";

        String affectedText = isDryRun ?
                "- Users that would be affected: " :
                "- Total users affected: ";

        String pointsText = isDryRun ?
                "- GOTW points that would be removed: " :
                "- Total GOTW points removed: ";

        String alertText = isDryRun ?
                "- Hotel Alert Would Be Sent: " :
                "- Hotel Alert Sent: ";

        String detailedMessage = headerText +
                "--------------------------------\r\n" +
                statsText + "\r" +
                "- Online users " + (isDryRun ? "with points: " : "reset: ") + onlineResetCount + "\r" +
                "- Offline users " + (isDryRun ? "with points: " : "reset: ") + offlineResetCount + "\r" +
                affectedText + (onlineResetCount + offlineResetCount) + "\r" +
                pointsText + totalPointsBefore + "\r\n" +
                topUsersSection.toString() + "\r\n" +
                "<b>Performance</b>\r" +
                "- Operation completed in: " + executionTime + "ms\r\n" +
                "<b>Hotel Alert</b>\r" +
                alertText + (alertSent ? "Yes" : "No") +
                (alertSent ? "" : " (use -alert parameter to send an alert)") + "\r\n" +
                (isDryRun ? "<b>NOTE: This was a simulation only. No changes were made.</b>\r\n" : "") +
                "\r";

        // Show detailed alert to command executor
        gameClient.getHabbo().alert(detailedMessage);
    }

    /**
     * Sends a confirmation whisper to the command executor
     */
    private void sendConfirmationWhisper(GameClient gameClient, boolean alertSent, boolean isDryRun) {
        String messageKey = isDryRun ? Messages.DRYRUN_FEEDBACK : Messages.COMMAND_FEEDBACK;
        String defaultMessage = isDryRun ?
                "GOTW currency reset simulation complete! See the alert for details." :
                "GOTW currency has been reset successfully! See the alert for details.";

        String message = Emulator.getTexts().getValue(messageKey, defaultMessage);

        if (isDryRun) {
            message += " No actual changes were made.";
        } else if (!alertSent) {
            message += " No hotel alert was sent.";
        }

        gameClient.getHabbo().whisper(message, RoomChatMessageBubbles.ALERT);
    }

    /**
     * Sends a whisper message with the given message key
     */
    private void sendWhisper(GameClient gc, String messageKey, String... replacements) {
        String message = Emulator.getTexts().getValue(messageKey);
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        gc.getHabbo().whisper(message, RoomChatMessageBubbles.ALERT);
    }
}