/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
 
package ballistickemu.Game.handlers;
import ballistickemu.Types.StickClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 *
 * @author Simon
 */
public class KillHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(KillHandler.class);
    
    public static void HandlePacket(StickClient client, String packet)
    {
        if (client == null || client.getRoom() == null || packet.length() < 4) {
            return;
        }
        
        try {
            // Extract killer ID (faster than substring)
            String killerId = packet.substring(1, 4);
            
            // Update killer stats
            StickClient killer = client.getRoom().GetCR().getClientfromUID(killerId);
            if (killer != null) {
                killer.setGameKills(killer.getGameKills() + 1);
            }
            
            // Update victim stats
            client.setGameDeaths(client.getGameDeaths() + 1);
            
            // Broadcast kill packet immediately
            client.getRoom().BroadcastToRoom(new StickPacket(packet));
            
        } catch (Exception e) {
            LOGGER.error("Error handling kill packet: {}", packet, e);
        }
    }
 
}
