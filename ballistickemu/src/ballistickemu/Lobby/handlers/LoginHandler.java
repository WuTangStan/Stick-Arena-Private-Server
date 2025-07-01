package ballistickemu.Lobby.handlers;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ballistickemu.Main;
import ballistickemu.Tools.DatabaseTools;
import ballistickemu.Tools.PasswordHasher;
import ballistickemu.Tools.StickPacketMaker;
import ballistickemu.Tools.StringTool;
import ballistickemu.Types.StickClient;
import ballistickemu.Types.StickColour;
import ballistickemu.Types.StickItem;

public class LoginHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(LoginHandler.class);

	public static void HandlePacket(StickClient client, String packet) {
		DatabaseTools.lock.lock();
		try (Connection conn = DatabaseTools.getDbConnection()) {
			// IP ban check
			try (PreparedStatement ps = conn.prepareStatement("SELECT * from `ipbans` where `ip` = ? ORDER BY id DESC LIMIT 1")) {
				ps.setString(1, client.getIoSession().getRemoteAddress().toString().substring(1).split(":")[0]);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						BigDecimal dec = rs.getBigDecimal("enddate");
						if (dec.longValue() < System.currentTimeMillis()) {
							try (PreparedStatement ps2 = conn.prepareStatement("DELETE FROM `ipbans` WHERE `ip` = ?")) {
								ps2.setString(1, client.getIoSession().getRemoteAddress().toString().substring(1).split(":")[0]);
								ps2.execute();
							}
						} else {
							client.write(StickPacketMaker.getErrorPacket("1"));
							return;
						}
					}
				}
			} catch (SQLException e) {
				LOGGER.warn("Exception checking IP ban tables: ", e);
			}
		} catch (SQLException e) {
			LOGGER.warn("Error during IP ban check DB connection", e);
		} finally {
			DatabaseTools.lock.unlock();
		}

		String[] splitted = packet.replaceAll("\0", "").substring(2).split(";");
		if (splitted[0].length() > 20) {
			client.getIoSession().close(true);
			return;
		}

		String MD5Pass = "";
		try {
			MD5Pass = PasswordHasher.generateHashedPassword(splitted[1]);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		try (Connection conn = DatabaseTools.getDbConnection()) {
			PreparedStatement ps = conn.prepareStatement("SELECT * FROM `users` WHERE `USERname` = ? AND BINARY `USERpass` = ?");
			ps.setString(1, splitted[0]);
			ps.setString(2, MD5Pass);
			ResultSet rs = ps.executeQuery();
			rs.last();
			int rowCount = rs.getRow();

			if (rowCount < 1) {
				client.write(StickPacketMaker.getLoginFailed());
				return;
			}

			String paddedUN = StringTool.PadStringLeft(splitted[0], "#", 20);
			String red = StringTool.PadStringLeft(String.valueOf(rs.getInt("red")), "0", 3);
			String green = StringTool.PadStringLeft(String.valueOf(rs.getInt("green")), "0", 3);
			String blue = StringTool.PadStringLeft(String.valueOf(rs.getInt("blue")), "0", 3);
			String colour = red + green + blue;

			int kills = rs.getInt("kills");
			int deaths = rs.getInt("deaths");
			int wins = rs.getInt("wins");
			int losses = rs.getInt("losses");
			int rounds = rs.getInt("rounds");
			int expiry = rs.getInt("passexpiry");
			int cash = rs.getInt("cash");
			int ticket = rs.getInt("ticket");
			int labpass = rs.getInt("labpass");
			int user_level = rs.getInt("user_level");
			int dbID = rs.getInt("UID");

			if (ticket != 1) {
				long lastTicket = rs.getBigDecimal("lastticket").longValue();
				if ((lastTicket + (28800000L)) <= System.currentTimeMillis()) {
					try (PreparedStatement ps2 = conn.prepareStatement("UPDATE `users` SET `ticket` = 1 WHERE `UID` = ?")) {
						ps2.setInt(1, dbID);
						ps2.execute();
						ticket = 1;
					}
				}
			}

			StickClient SC = Main.getLobbyServer().getClientRegistry().getClientfromName(splitted[0]);
			if (SC != null)
				SC.getSecondaryLoginPacket();

			client.setName(splitted[0]);
			client.setColour1(colour);
			client.setColour2(colour);
			client.setKills(kills);
			client.setDeaths(deaths);
			client.setWins(wins);
			client.setLosses(losses);
			client.setRounds(rounds);
			client.setPassExpiry(expiry);
			client.setCash(cash);
			client.setTicket(ticket);
			client.setPass(labpass == 1);
			client.setModStatus(user_level > 0);
			client.setUserLevel(user_level);
			client.setDbID(dbID);

			boolean ready = false;
			boolean skipCheck = false;

			if (rs.getInt("ban") == 1) {
				try (PreparedStatement ps1 = conn.prepareStatement("SELECT id, enddate FROM `bans` WHERE `userid` = ? ORDER BY id DESC LIMIT 1")) {
					ps1.setInt(1, dbID);
					ResultSet rs1 = ps1.executeQuery();
					if (rs1.next()) {
						BigDecimal dec = rs1.getBigDecimal("enddate");
						if (dec.longValue() < System.currentTimeMillis()) {
							try (PreparedStatement ps2 = conn.prepareStatement("UPDATE `users` SET `ban` = 0 WHERE `UID` = ?")) {
								ps2.setInt(1, dbID);
								ps2.execute();
							}
						} else {
							client.write(StickPacketMaker.getErrorPacket("1"));
							return;
						}
					}
				}
			}

			while (!ready) {
				try (PreparedStatement psz = conn.prepareStatement("SELECT * FROM `inventory` WHERE `userid` = ?")) {
					psz.setInt(1, dbID);
					rs = psz.executeQuery();
					rs.last();

					if (rs.getRow() > 1)
						skipCheck = false;

					if (rs.getRow() < 2) {
						try (PreparedStatement delete = conn.prepareStatement("DELETE FROM `inventory` where `userid` = ?")) {
							delete.setInt(1, dbID);
							delete.executeUpdate();
						}

						try (PreparedStatement psx1 = conn.prepareStatement("INSERT INTO `inventory` ... VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
							psx1.setInt(1, dbID);
							psx1.setInt(2, 100);
							psx1.setInt(3, 1);
							psx1.setInt(4, client.getStickColour().getRed1());
							psx1.setInt(5, client.getStickColour().getGreen1());
							psx1.setInt(6, client.getStickColour().getBlue1());
							psx1.setInt(7, client.getStickColour().getRed2());
							psx1.setInt(8, client.getStickColour().getGreen2());
							psx1.setInt(9, client.getStickColour().getBlue2());
							psx1.setInt(10, 1);
							psx1.executeUpdate();
						}

						try (PreparedStatement psx2 = conn.prepareStatement("INSERT INTO `inventory` ... VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
							psx2.setInt(1, dbID);
							psx2.setInt(2, 200);
							psx2.setInt(3, 2);
							psx2.setInt(4, 0);
							psx2.setInt(5, 0);
							psx2.setInt(6, 0);
							psx2.setInt(7, 0);
							psx2.setInt(8, 0);
							psx2.setInt(9, 0);
							psx2.setInt(10, 1);
							psx2.executeUpdate();
						}
						skipCheck = true;
					}

					if (!skipCheck) {
						try (PreparedStatement psy = conn.prepareStatement("SELECT * FROM `inventory` WHERE `userid` = ?")) {
							psy.setInt(1, dbID);
							ResultSet rs1 = psy.executeQuery();
							while (rs1.next()) {
								StickColour _colour = new StickColour(rs1.getInt("red1"), rs1.getInt("green1"),
										rs1.getInt("blue1"), rs1.getInt("red2"), rs1.getInt("green2"), rs1.getInt("blue2"));
								int itemDBID = rs1.getInt("id");
								int itemID = rs1.getInt("itemid");

								client.getInventory().put(itemDBID, new StickItem(itemID, itemDBID, dbID,
										rs1.getInt("itemtype"), rs1.getInt("selected") == 1, _colour));
							}
							ready = true;
						}
					}
				}
			}

			// Final login info
			colour = client.getSelectedSpinner().getColour().getColour1AsString();
			String colour2 = client.getSelectedSpinner().getColour().getColour2AsString();

			updateLastLoginDate(conn, dbID, client.getIoSession().getRemoteAddress().toString().substring(1).split(":")[0]);

			try (PreparedStatement updateOnline = conn.prepareStatement("UPDATE `users` SET `isOnline` = 1 WHERE `UID` = ?")) {
				updateOnline.setInt(1, dbID);
				updateOnline.executeUpdate();
			}

			client.write(StickPacketMaker.getLoginSuccess(client.getUID(), paddedUN, colour, colour2, kills, deaths,
					wins, losses, rounds, labpass, expiry, ticket, cash, user_level));
			Main.getLobbyServer().getClientRegistry().registerClient(client);
			client.setIsReal(true);

		} catch (SQLException e) {
			LOGGER.warn("Exception at login", e);
		}
	}

	private static void updateLastLoginDate(Connection conn, int dbID, String ip) {
		try (PreparedStatement ps1 = conn.prepareStatement("UPDATE `users` SET `lastlogindate` = ?, `ip` = ? WHERE `UID` = ?")) {
			ps1.setLong(1, System.currentTimeMillis());
			ps1.setString(2, ip);
			ps1.setInt(3, dbID);
			ps1.executeUpdate();
		} catch (SQLException e) {
			LOGGER.warn("Error while update last login date for ID: " + dbID, e);
		}
	}
}
