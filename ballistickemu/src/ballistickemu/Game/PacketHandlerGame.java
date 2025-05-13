/*
 *     THIS FILE AND PROJECT IS SUPPLIED FOR EDUCATIONAL PURPOSES ONLY.
 *
 *     This program is free software; you can redistribute it
 *     and/or modify it under the terms of the GNU General
 *     Public License as published by the Free Software
 *     Foundation; either version 2 of the License, or (at your
 *     option) any later version.
 *
 *     This program is distributed in the hope that it will be
 *     useful, but WITHOUT ANY WARRANTY; without even the
 *     implied warranty of MERCHANTABILITY or FITNESS FOR A
 *     PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General
 *     Public License along with this program; if not, write to
 *     the Free Software Foundation, Inc., 59 Temple Place,
 */
package ballistickemu.Game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ballistickemu.Game.handlers.GamePacketBroadcastHandler;
import ballistickemu.Game.handlers.KillHandler;
import ballistickemu.Game.handlers.MapRatingHandler;
import ballistickemu.Game.handlers.SetMapCycleListHandler;
import ballistickemu.Game.handlers.VoteKickHandler;
import ballistickemu.Lobby.handlers.FindRequestHandler;
import ballistickemu.Lobby.handlers.GeneralChatHandler;
import ballistickemu.Lobby.handlers.GenericSendDataHandler;
import ballistickemu.Lobby.handlers.MapCycleRequestHandler;
import ballistickemu.Lobby.handlers.ModBanHandler;
import ballistickemu.Lobby.handlers.ModBanNameHandler;
import ballistickemu.Lobby.handlers.ModGlobalHandler;
import ballistickemu.Lobby.handlers.ModRequestIPHandler;
import ballistickemu.Lobby.handlers.ModWarnHandler;
import ballistickemu.Lobby.handlers.NewClientHandler;
import ballistickemu.Lobby.handlers.RoomDetailRequestHandler;
import ballistickemu.Lobby.handlers.RoomRequestHandler;
import ballistickemu.Types.StickClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 *
 * @author Simon
 */
public class PacketHandlerGame {
	private static final Logger LOGGER = LoggerFactory.getLogger(PacketHandlerGame.class);
	
	// Packet type registry for faster lookups
	private static final Map<String, BiConsumer<StickClient, String>> PACKET_HANDLERS = new ConcurrentHashMap<>();
	
	static {
		// Initialize packet handlers
		PACKET_HANDLERS.put("01", (client, packet) -> RoomRequestHandler.handlePacket(client));
		PACKET_HANDLERS.put("03", (client, packet) -> NewClientHandler.HandlePacket(client, packet));
		PACKET_HANDLERS.put("04", (client, packet) -> RoomDetailRequestHandler.HandlePacket(client, packet));
		PACKET_HANDLERS.put("05", (client, packet) -> SetMapCycleListHandler.HandlePacket(client, packet));
		PACKET_HANDLERS.put("06", (client, packet) -> MapCycleRequestHandler.HandlePacket(client, packet));
		PACKET_HANDLERS.put("0g", (client, packet) -> ModWarnHandler.HandlePacket(client, packet));
		PACKET_HANDLERS.put("0f", (client, packet) -> ModBanHandler.HandlePacket(client, packet));
		PACKET_HANDLERS.put("0j", (client, packet) -> ModGlobalHandler.HandlePacket(client, packet));
		PACKET_HANDLERS.put("07", (client, packet) -> ModRequestIPHandler.HandlePacket(client, packet));
		PACKET_HANDLERS.put("0l", (client, packet) -> ModBanNameHandler.HandlePacket(client, packet));
		PACKET_HANDLERS.put("0h", (client, packet) -> FindRequestHandler.HandlePacket(client, packet));
		PACKET_HANDLERS.put("0i", (client, packet) -> MapRatingHandler.HandlePacket(client, packet));
	}

	public static void HandlePacket(String packet, StickClient client) {
		if (packet == null || packet.length() < 2) {
			return;
		}

		try {
			String packetType = packet.substring(0, 2);
			
			// Handle game packets (1, 2, 4, 5, 6, 8)
			if ("124568".indexOf(packet.charAt(0)) >= 0) {
				GamePacketBroadcastHandler.HandlePacket(client, packet);
				return;
			}
			
			// Handle kill packets
			if (packet.charAt(0) == '7') {
				KillHandler.HandlePacket(client, packet);
				return;
			}
			
			// Handle vote kick packets
			if (packet.charAt(0) == 'K') {
				VoteKickHandler.HandlePacket(client, packet);
				return;
			}
			
			// Handle lobby packets
			if (packet.charAt(0) == '0') {
				BiConsumer<StickClient, String> handler = PACKET_HANDLERS.get(packetType);
				if (handler != null) {
					handler.accept(client, packet);
				} else {
					GenericSendDataHandler.HandlePacket(client, packet);
				}
				return;
			}
			
			// Handle chat packets
			GeneralChatHandler.HandlePacket(client, packet);
			
		} catch (Exception e) {
			LOGGER.error("Error handling packet: {}", packet, e);
		}
	}
}
