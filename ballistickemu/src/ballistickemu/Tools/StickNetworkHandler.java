package ballistickemu.Tools;

import java.util.Calendar;
import java.util.TimeZone;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ballistickemu.Main;
import ballistickemu.Game.PacketHandlerGame;
import ballistickemu.Lobby.PacketHandlerLobby;
import ballistickemu.Types.StickClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class StickNetworkHandler extends IoHandlerAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger(StickNetworkHandler.class);

	public StickNetworkHandler() {
	}

	@Override
	public void sessionOpened(IoSession session) throws Exception {
		int rowCount = -1;
		LOGGER.info("IoSession with {} opened at {}", session.getRemoteAddress(),
				Calendar.getInstance(TimeZone.getDefault()).getTime().toString());
		if (session.getRemoteAddress() == null)
			return;
		if (rowCount > 0) {
			session.close(false);
			return;
		}

		StickClient newClient = new StickClient(session,
				UIDTool.GenerateUID(Main.getLobbyServer().getClientRegistry()));
		session.setAttribute(StickClient.CLIENT_KEY, newClient);
		LOGGER.info("Created new client with UID: {} for session: {}", newClient.getUID(), session.getRemoteAddress());
	}

	@Override
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
	}

	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		String S = message.toString().trim();
		StickClient c_Client = (StickClient) session.getAttribute(StickClient.CLIENT_KEY);
		
		// Handle special IP message from WebSocket proxy
		if (S.startsWith("[IP:")) {
			if (c_Client == null) {
				LOGGER.warn("Received IP message but client is null, session: {}", session.getRemoteAddress());
				return;
			}
			// Extract IP from [IP:xxx]\n format
			String realIP = S.substring(4); // Remove "[IP:"
			int endBracket = realIP.indexOf(']');
			if (endBracket != -1) {
				realIP = realIP.substring(0, endBracket); // Remove "]"
			}
			c_Client.setRealClientIP(realIP);
			LOGGER.info("Real client IP set to: {} for client: {}", realIP, c_Client.getUID());
			
			// If client is already logged in, update the database with the correct IP
			if (c_Client.getDbID() > 0 && c_Client.getName() != null) {
				try (Connection conn = DatabaseTools.getDbConnection()) {
					// Get current IP from database
					String currentDbIP = null;
					try (PreparedStatement psCheck = conn.prepareStatement("SELECT ip FROM `users` WHERE `UID` = ?")) {
						psCheck.setInt(1, c_Client.getDbID());
						try (ResultSet rs = psCheck.executeQuery()) {
							if (rs.next()) {
								currentDbIP = rs.getString("ip");
							}
						}
					}
					
					// Update IP
					try (PreparedStatement ps = conn.prepareStatement("UPDATE `users` SET `ip` = ? WHERE `UID` = ?")) {
						ps.setString(1, realIP);
						ps.setInt(2, c_Client.getDbID());
						ps.executeUpdate();
						
						if (currentDbIP != null && !currentDbIP.equals(realIP)) {
							LOGGER.info("Updated database IP for user " + c_Client.getName() + " (UID: " + c_Client.getDbID() + ") from " + currentDbIP + " to " + realIP);
						} else {
							LOGGER.info("Set database IP for user " + c_Client.getName() + " (UID: " + c_Client.getDbID() + ") to " + realIP);
						}
					}
				} catch (SQLException e) {
					LOGGER.warn("Error updating database IP for user: " + c_Client.getName(), e);
				}
			}
			return;
		}
		
		if (c_Client == null) {
			LOGGER.warn("Received message but client is null: {}", S);
			return;
		}

		if (S.equalsIgnoreCase("<policy-file-request/>")) {
			c_Client.setReceivingPolicy(true);
			c_Client.writePolicyFile();
			return;
		}

		if (S.length() <= 1) {
			return;
		}

		String fix = S + "\0";
		if (c_Client.getLobbyStatus() || (S.substring(0, 2).equalsIgnoreCase("03")
				|| (c_Client.getName() == null && c_Client.getLobbyStatus()))) {
			PacketHandlerLobby.HandlePacket(fix, c_Client);
		} else if (!c_Client.getLobbyStatus()) {
			PacketHandlerGame.HandlePacket(fix, c_Client);
		}
	}

	@Override
	public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
		if (session.getIdleCount(status) > 50) {
			session.close(true);
		}
	}

	@Override
	public void sessionClosed(IoSession session) throws Exception {
		synchronized (session) {
			StickClient c_Client = (StickClient) session.getAttribute(StickClient.CLIENT_KEY);
			if (c_Client == null)
				return;

			if (c_Client.getLobbyStatus()) {
				Main.getLobbyServer().getClientRegistry().deregisterClient(c_Client);
			} else if (c_Client.getRoom() != null) {
				c_Client.getRoom().GetCR().deregisterClient(c_Client);
				Main.getLobbyServer().getClientRegistry().deregisterClient(c_Client);
			}

			try (Connection conn = DatabaseTools.getDbConnection();
				 PreparedStatement updateOffline = conn.prepareStatement("UPDATE `users` SET `isOnline` = 0 WHERE `UID` = ?")) {
				updateOffline.setInt(1, c_Client.getDbID());
				updateOffline.executeUpdate();
			} catch (SQLException e) {
				LOGGER.warn("Error while setting isOnline=0 for UID: " + c_Client.getDbID(), e);
			}
		}
	}
}
