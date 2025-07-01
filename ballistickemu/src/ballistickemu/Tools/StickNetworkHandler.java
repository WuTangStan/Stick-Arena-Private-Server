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
	}

	@Override
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
	}

	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		String S = message.toString().trim();
		StickClient c_Client = (StickClient) session.getAttribute(StickClient.CLIENT_KEY);
		if (c_Client == null) {
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
