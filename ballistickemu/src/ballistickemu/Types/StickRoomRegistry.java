package ballistickemu.Types;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class StickRoomRegistry {
	private LinkedHashMap<String, StickRoom> RoomList;
	private Map<String, ScheduledFuture<?>> executors = new HashMap<>();
	private ScheduledExecutorService STP;
	public ReentrantReadWriteLock RoomLock = new ReentrantReadWriteLock(true);

	public LinkedHashMap<String, StickRoom> getRoomList() {
		return this.RoomList;
	}

	public StickRoomRegistry() {
		this.STP = new ScheduledThreadPoolExecutor(3);
		this.RoomList = new LinkedHashMap<>();
	}

	public Collection<StickRoom> GetAllRooms() {
		return this.RoomList.values();
	}

	public void RegisterRoom(StickRoom room) {
		this.RoomLock.writeLock().lock();

		try {
			this.RoomList.put(room.getName(), room);

		} finally {
			this.RoomLock.writeLock().unlock();
		}
	}
	
	public void deRegisterRoom(String name) {
		this.RoomLock.writeLock().lock();
		try {
			RoomList.remove(name);
			executors.get(name).cancel(true);
			executors.remove(name);
		} finally {
			this.RoomLock.writeLock().unlock();
		}
	}

	public void scheduleRoomTimer(String name, Runnable r) {
		executors.put(name, this.STP.scheduleAtFixedRate(r, 0L, 1L, TimeUnit.SECONDS));
	}

	public boolean RoomExists(String Name) {
		return !(GetRoomFromName(Name) == null);
	}

	public StickRoom GetRoomFromName(String Name) {
		this.RoomLock.readLock().lock();
		try {
			for (StickRoom S : GetAllRooms()) {
				if (S.getName().equalsIgnoreCase(Name))
					return S;
			}
		} finally {
			this.RoomLock.readLock().unlock();
		}
		return null;
	}

	public String GetRoomPacketInfo(StickClient client) {
		this.RoomLock.readLock().lock();
		try {
			StringBuilder SB = new StringBuilder();
			SB.append("_0;");
			for (StickRoom S : GetAllRooms()) {
				if (!S.getPrivacy().booleanValue() && !S.isFull(client)
						&& (!S.getNeedsPass() || client.getPass() || S.getVIPs().containsValue(client))) {
					SB.append(S.getName());

					if (S.getNeedsPass().booleanValue()) {
						SB.append("1");
					} else
						SB.append("0");
					SB.append(";");
				}
			}

			return SB.toString();
		} finally {
			this.RoomLock.readLock().unlock();
		}
	}
}
