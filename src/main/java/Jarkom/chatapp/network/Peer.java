package Jarkom.chatapp.network;

import Jarkom.chatapp.models.Message;
import Jarkom.chatapp.models.Room;
import java.io.*;
import java.net.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class Peer implements Server.PacketListener {

    public final String username;
    public final String hostIp;
    private final String subnet;
    private static final int chatPort = 5000;

    private final Server server;
    private Client chatClient;

    private final HashSet<String> seenPacketIDs = new HashSet<>();
    public final Map<String, Room> knownRooms = new ConcurrentHashMap<>();
    public Room activeChatRoom;
    private String lastJoinRequesterHost;

    private final Object leaveLock = new Object();
    private final Object clientLock = new Object();
    private boolean leaveConfirmed = false;

    private boolean waitingRoomResponse = true;
    private boolean roomRequestAccepted = false;

    private final CountDownLatch serverReadyLatch = new CountDownLatch(1);
    private volatile boolean isJoining = false;

    public static interface ChatMessageListener {
        void onChatMessage(String formattedMessage);
    }

    // 2) Store listeners
    private final CopyOnWriteArrayList<ChatMessageListener> chatListeners = new CopyOnWriteArrayList<>();

    public void addChatMessageListener(ChatMessageListener l) {
        chatListeners.addIfAbsent(l);
    }

    public void removeChatMessageListener(ChatMessageListener l) {
        chatListeners.remove(l);
    }

    private void handleChatMessage(String[] parts) {
        // parts: [0]=roomName, [1]=senderIP, [2]=senderName, [3]=content, [4]=msgId
        String senderIP = parts[1];
        String senderName = parts[2];
        String content = parts[3];

        // *** IGNORE your own echo ***
        if (senderIP.equals(hostIp)) {
            return;
        }

        // Build “[name] [ip]\nmessage”
        String formatted = String.format("[%s] [%s]\n%s",
                senderName, senderIP, content);

        // dispatch to UI listeners
        for (ChatMessageListener l : chatListeners) {
            l.onChatMessage(formatted);
        }
    }

    public Peer(String username) {
        this.username = username;
        try {
            this.hostIp = InetAddress.getLocalHost().getHostAddress();
            this.subnet = hostIp.substring(0, hostIp.lastIndexOf(".") + 1);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Tidak bisa menemukan IP lokal", e);
        }

        // 1. Buat komponen Server dan daftarkan Peer ini sebagai pendengar
        this.server = new Server(chatPort, this);
        this.server.setPacketListener(this);
        this.server.start();
        System.out.println("SERVER STARTED");

        // 2. Inisialisasi awal client (terhubung ke diri sendiri)
        this.chatClient = new Client(hostIp, chatPort);
        this.chatClient.initConnection();
        System.out.println("CLIENT SOCKET STARTED");

        // 3. Jalankan discovery dan input user
        new Thread(this::discoverAndJoin).start();
        System.out.println("DISCOVER AND JOIN STARTED");
    }

    @Override
    public void onPacketReceived(String packet, DataOutputStream replyStream) {
        String[] parts = packet.split("\\|", 2);
        String type = parts[0], payload = parts.length > 1 ? parts[1] : "";
        System.out.println("RECEIVED " + type);

        try {
            // —— single‐step join handshake ——
            if ("JOIN_NETWORK".equals(type)) {
                String newPeerIp = payload;
                String oldSucc = chatClient.destinationHost;
                // rewire self (Y→X)
                synchronized (clientLock) {
                    chatClient = new Client(newPeerIp, chatPort);
                    chatClient.initConnection();
                }
                // reply telling X to hook up to oldSucc
                replyStream.writeUTF("SUCCESSOR|" + oldSucc);
                replyStream.flush();
                System.out.printf("[SYSTEM] JOIN_NETWORK: rewired %s→%s, told peer to use %s%n",
                        hostIp, newPeerIp, oldSucc);
                return;
            }
            // —— joiner consumes SUCCESSOR ——
            else if ("SUCCESSOR".equals(type)) {
                String succIp = payload;
                synchronized (clientLock) {
                    chatClient = new Client(succIp, chatPort);
                    chatClient.initConnection();
                }
                System.out.println("[SYSTEM] SUCCESSOR received; downstream now " + succIp);
                return;
            } else if ("ROOM_REQUEST".equals(type)) {
                // payload = roomName|requesterIp|requesterUsername
                String[] p = payload.split("\\|", 3);
                String roomName = p[0];
                String requesterIp = p[1];
                String requesterUser = p[2];

                Room room = knownRooms.get(roomName);
                // if I'm the owner, decide; otherwise just forward
                if (room != null && room.getOwner().equals(hostIp)) {
                    // decide accept or deny
                    boolean banned = room.isBanned(requesterIp);
                    String decision = banned ? "DENY" : "ACCEPT";

                    // reply on same socket
                    replyStream.writeUTF("ROOM_RESPONSE|" + decision);
                    replyStream.flush();

                    // if accepted, announce join to the ring
                    if (!banned) {
                        String joinPkt = String.format(
                                "JOIN_ROOM|%s|%s|%s",
                                roomName, requesterIp, requesterUser);
                        seenPacketIDs.add(joinPkt);
                        forwardGossip(joinPkt);
                    }
                } else {
                    // not my request—pass it along
                    forwardGossip(packet);
                }
                return;
            }
            // —— leave network handshake ——
            else if ("LEAVE_NETWORK".equals(type)) {
                String[] p = payload.split("\\|", 3);
                String origin = p[0], linkIp = p[1];
                int linkPort = Integer.parseInt(p[2]);
                if (linkIp.equals(chatClient.destinationHost)) {
                    leaveAck(origin, linkIp, linkPort);
                } else {
                    forwardGossip(packet);
                }
                return;
            } else if ("LEAVE_ACK".equals(type)) {
                if (!payload.equals(hostIp))
                    return;
                synchronized (leaveLock) {
                    leaveConfirmed = true;
                    leaveLock.notifyAll();
                }
                return;
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }

        // —— gossip messages ——
        if (seenPacketIDs.add(packet)) {
            String[] p = payload.split("\\|", 3);
            switch (type) {
                case "CHAT":
                    String[] chatParts = payload.split("\\|", 5);
                    String roomName = chatParts[0];

                    // only handle if there *is* an active room & names match
                    if (activeChatRoom != null
                            && activeChatRoom.getName().equals(roomName)) {
                        handleChatMessage(chatParts);
                    }
                    break;
                case "ROOM_ANNOUNCE":
                    handleRoomAnnouncement(p[0], p[1], p[2]);
                    break;
                case "JOIN_ROOM":
                    handleJoinRoom(p[0], p[1], p[2]);
                    break;
                case "EXIT_ROOM":
                    handleExitRoom(p[0], p[1], p[2]);
                    break;
                case "BAN_ANNOUNCE":
                    handleBanAnnounce(p[0], p[1]);
                    break;
            }
            forwardGossip(packet);
        }
    }

    public void leaveNetwork() {
        // jika hanya ada 1 peer di network, langsung matikan
        if (chatClient.destinationHost.equals(hostIp)) {
            System.out.println("[SISTEM] Mematikan Network");
            chatClient.closeConnection();
            System.exit(0);
        }
        String packet = "LEAVE_NETWORK|" + hostIp + "|" + chatClient.destinationHost + "|" + chatClient.destinationPort;

        synchronized (clientLock) {
            chatClient.forwardPacket(packet);
        }
        System.out.println("[SISTEM] Mengirim notifikasi LEAVE");

        // Tunggu sampai menerima ack dari predecessor di network
        synchronized (leaveLock) {
            while (!leaveConfirmed) {
                try {
                    leaveLock.wait(5_000); // 5s timeout to avoid infinite block
                    if (!leaveConfirmed) {
                        System.err.println("[SISTEM] No LEAVE_ACK received—retrying LEAVE");
                        chatClient.forwardPacket(packet);
                    }
                } catch (InterruptedException e) {
                    /* ignore */ }
            }
        }

        System.out.println("[SISTEM] Predecessor rewired; safe to exit.");
        chatClient.closeConnection();
        System.exit(0);
    }

    private void leaveAck(String originIp, String newSuccIp, int newSuccPort) {
        synchronized (clientLock) {
            // 1) Tell the leaving peer we saw its LEAVE_NETWORK
            Client ackClient = new Client(originIp, chatPort);
            ackClient.forwardPacket("LEAVE_ACK|" + hostIp);
            // 2) Re‑wire our downstream to skip the leaver
            chatClient = new Client(newSuccIp, newSuccPort);
        }
        System.out.printf("[SYSTEM] %s left; now downstream is %s:%d%n",
                originIp, newSuccIp, newSuccPort);
    }

    public void handleJoinRoom(String roomName, String ip, String username) {
        Room chatRoom = knownRooms.get(roomName);
        if (chatRoom == null)
            return;
        chatRoom.addUser(ip, username);

        // Only fire a SYSTEM message if *you* are in that room:
        if (activeChatRoom != null && activeChatRoom.getName().equals(roomName)) {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            String sysMsg = String.format("[SYSTEM] [%s]\n%s has joined the room",
                    time, username);
            // notify UI
            for (ChatMessageListener l : chatListeners) {
                l.onChatMessage(sysMsg);
            }
        }
    }

    public void handleExitRoom(String roomName, String ip, String username) {
        Room chatRoom = knownRooms.get(roomName);
        if (chatRoom == null)
            return;
        chatRoom.removeUser(ip);

        // Only fire a SYSTEM message if *you* are in that room:
        if (activeChatRoom != null && activeChatRoom.getName().equals(roomName)) {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            String sysMsg = String.format("[SYSTEM] [%s]\n%s has left the room",
                    time, username);
            // notify UI
            for (ChatMessageListener l : chatListeners) {
                l.onChatMessage(sysMsg);
            }
        }
    }

    private void handleRoomAnnouncement(String roomName, String owner, String date) {
        System.out.println("RECEIVED ROOM ANOUNCE " + roomName);
        // knownRooms.computeIfAbsent(roomName, k -> new Room(k, owner, date));
        knownRooms.put(roomName, new Room(roomName, owner, date));
    }

    private void handleBanAnnounce(String roomName, String bannedIp) {
        Room chatRoom = knownRooms.get(roomName);
        if (chatRoom != null) {
            chatRoom.banUser(bannedIp);
        } else {
            System.out.println("Room tidak ditemukan");
        }
    }

    /**
     * Scans the LAN, does a one‐step JOIN_NETWORK → SUCCESSOR handshake,
     * then installs the new downstream client.
     */
    private void discoverAndJoin() {
        try {
            serverReadyLatch.await();
        } catch (InterruptedException ignored) {
        }

        System.out.println("[SYSTEM] Scanning for peers...");
        for (int i = 1; i <= 254; i++) {
            String ip = subnet + i;
            System.out.println(ip);
            if (ip.equals(hostIp))
                continue;

            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, chatPort), 200);

                // Once connected, do the single‐step join handshake
                try (DataOutputStream out = new DataOutputStream(s.getOutputStream());
                        DataInputStream in = new DataInputStream(s.getInputStream())) {

                    out.writeUTF("JOIN_NETWORK|" + hostIp);
                    String resp = in.readUTF(); // e.g. "SUCCESSOR|10.0.0.5"
                    String succIp = resp.split("\\|", 2)[1];

                    synchronized (clientLock) {
                        chatClient = new Client(succIp, chatPort);
                    }
                    System.out.println("[SYSTEM] Joined via " + ip + "; downstream is " + succIp);
                    return;
                }
            } catch (IOException e) {
                // Timeout or refused: try the next IP immediately
            }
        }

        System.out.println("[SYSTEM] No peers found; starting fresh ring.");
        // chatClient already points at self
    }

    public List<Room> getRoomList() {
        return new ArrayList<Room>(knownRooms.values());
    }

    public boolean joinRoom(String roomName) {
        Room room = knownRooms.get(roomName);
        if (room == null) {
            System.out.println("[SYSTEM] Room not found: " + roomName);
            return false;
        }
        String ownerIp = room.getOwner();

        try (Socket sock = new Socket(ownerIp, chatPort);
                DataOutputStream out = new DataOutputStream(sock.getOutputStream());
                DataInputStream in = new DataInputStream(sock.getInputStream())) {

            // send the request
            out.writeUTF(String.format(
                    "ROOM_REQUEST|%s|%s|%s",
                    roomName, hostIp, username));
            out.flush();

            // block until ACCEPT or DENY
            String resp = in.readUTF(); // e.g. "ROOM_RESPONSE|ACCEPT"
            if ("ROOM_RESPONSE|ACCEPT".equals(resp)) {
                System.out.println("[SYSTEM] Join accepted for " + roomName);
                this.activeChatRoom = room;
                return true;
            } else {
                System.out.println("[SYSTEM] Join denied for " + roomName);
                return false;
            }

        } catch (IOException e) {
            System.err.println("[SYSTEM] Failed to join room " + roomName + ": " + e.getMessage());
            return false;
        }
    }

    public void exitRoom(String roomName) {
        this.activeChatRoom = null;
        String packet = "EXIT_ROOM|" + roomName + "|" + hostIp + "|" + username;
        roomRequestAccepted = false;
        waitingRoomResponse = true;
        seenPacketIDs.add(packet);
        synchronized (clientLock) {
            handleExitRoom(roomName, hostIp, username);
            forwardGossip(packet);
        }
    }

    public void banUser(String roomName, String bannedIp) {
        Room chatRoom = knownRooms.get(roomName);
        if (chatRoom.getOwner().equals(hostIp)) {
            String bannedUsername = chatRoom.banUser(bannedIp);
            if (bannedUsername == null) {
                System.out.printf("Tidak ada user dalam %s dengan ip tersebut\n", roomName);
                return;
            }
            System.out.printf("Banning %s:%s dari %s", bannedUsername, bannedIp, roomName);
            String packet = "BAN_ANNOUNCE|" + roomName + "|" + bannedIp;
            seenPacketIDs.add(packet);
            synchronized (clientLock) {
                chatClient.forwardPacket(packet);
            }
        } else {
            System.out.println("User tidak punya hak pemilik");
        }
    }

    public void sendMessage(String roomName, String message) {
        String msgId = UUID.randomUUID().toString();
        String packet = String.format("CHAT|%s|%s|%s|%s|%s", roomName, hostIp, username, message, msgId);
        seenPacketIDs.add(packet);
        synchronized (clientLock) {
            chatClient.forwardPacket(packet);
        }
    }

    public boolean createRoom(String roomName) {
        if (!knownRooms.containsKey(roomName)) {
            Room newRoom = new Room(roomName, this.hostIp, LocalDate.now().toString());
            knownRooms.put(roomName, newRoom);
            String packet = "ROOM_ANNOUNCE|" + roomName + "|" + this.hostIp + "|" + LocalDate.now().toString() + "|"
                    + username;
            seenPacketIDs.add(packet);
            synchronized (clientLock) {
                System.out.println("SENDING PACKET");
                chatClient.forwardPacket(packet);
            }
            return true;
        }
        return false;
    }

    public boolean isConnected() {
        return chatClient != null && chatClient.isConnectionActive();
    }

    public void signalServerReady() {
        serverReadyLatch.countDown();
    }

    /**
     * Send a packet onwards—with no self‐loops.
     */
    private void forwardGossip(String packet) {
        synchronized (clientLock) {
            if (!chatClient.destinationHost.equals(hostIp)) {
                chatClient.forwardPacket(packet);
            } else {
                System.out.println("[SYSTEM] skipping self‑forward of “" + packet + "”");
            }
        }
    }
}
