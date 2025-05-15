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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Simon
 */
public class GamePacketBroadcastHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GamePacketBroadcastHandler.class);

    // Optional performance tracking and caching
    private static final Map<String, StickPacket> PACKET_CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger packetCounter = new AtomicInteger(0);
    private static final AtomicInteger totalPackets = new AtomicInteger(0);
    private static final AtomicInteger cachedPackets = new AtomicInteger(0);

    public static void HandlePacket(StickClient client, String packet) {
        if (client == null || client.getRoom() == null) {
            return;
        }

        try {
            totalPackets.incrementAndGet();

            StickPacket packetToSend = StickPacketMaker.getBroadcastPacket(packet, client.getUID());
            client.getRoom().BroadcastToRoom(packetToSend);

            // Periodically clear cache and log metrics
            if (packetCounter.incrementAndGet() % 1000 == 0) {
                PACKET_CACHE.clear();
                packetCounter.set(0);

                int total = totalPackets.get();
                int cached = cachedPackets.get();
                if (total > 0) {
                    double cacheHitRate = (total - cached) * 100.0 / total;
                    LOGGER.info("Packet Cache Stats - Total: {}, Cache Hits: {}%", total, String.format("%.1f", cacheHitRate));
                }
                totalPackets.set(0);
                cachedPackets.set(0);
            }

        } catch (Exception e) {
            LOGGER.error("Error broadcasting game packet: {}", packet, e);
        }
    }
}
