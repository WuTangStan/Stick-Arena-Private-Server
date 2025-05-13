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
package ballistickemu.Game.handlers;

import ballistickemu.Tools.StickPacketMaker;
import ballistickemu.Types.StickClient;
import ballistickemu.Types.StickPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;

/**
 *
 * @author Simon
 */
public class GamePacketBroadcastHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GamePacketBroadcastHandler.class);
    
    public static void HandlePacket(StickClient client, String packet) {
        if (client == null || client.getRoom() == null) {
            return;
        }
        
        try {
            StickPacket stickPacket = new StickPacket();
            stickPacket.setData(packet);
            
            // Get all clients in the room except the sender
            ArrayList<StickClient> roomClients = new ArrayList<>(client.getRoom().GetCR().getAllClients());
            for (StickClient roomClient : roomClients) {
                if (roomClient != client && !roomClient.getLobbyStatus()) {
                    try {
                        roomClient.write(stickPacket);
                    } catch (Exception e) {
                        LOGGER.error("Error sending packet to client {}: {}", roomClient.getName(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error broadcasting game packet: {}", packet, e);
        }
    }
}
