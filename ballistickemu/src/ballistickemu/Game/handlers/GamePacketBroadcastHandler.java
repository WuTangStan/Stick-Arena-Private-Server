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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Simon
 */
public class GamePacketBroadcastHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GamePacketBroadcastHandler.class);
    
    // Cache for frequently used packets
    private static final Map<String, StickPacket> PACKET_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;
    private static int packetCounter = 0;
    
    public static void HandlePacket(StickClient client, String packet) {
        if (client == null || client.getRoom() == null) {
            return;
        }
        
        try {
            // Get or create cached packet
            StickPacket stickPacket = PACKET_CACHE.computeIfAbsent(packet, k -> {
                StickPacket newPacket = new StickPacket();
                newPacket.setData(packet);
                return newPacket;
            });
            
            // Broadcast to room
            client.getRoom().BroadcastToRoom(stickPacket);
            
            // Periodically clear cache to prevent memory growth
            if (++packetCounter % 1000 == 0) {
                PACKET_CACHE.clear();
                packetCounter = 0;
            }
            
        } catch (Exception e) {
            LOGGER.error("Error broadcasting game packet: {}", packet, e);
        }
    }
}
