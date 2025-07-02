package Jarkom.chatapp.network;

import Jarkom.chatapp.models.Room;
import java.io.*;
import java.net.*;
import java.time.LocalDate;
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
    private Room activeChatRoom;
    private String lastJoinRequesterHost;

    private final Object leaveLock = new Object();
    private final Object clientLock = new Object();
    private boolean leaveConfirmed = false;

    private boolean waitingRoomResponse = true;
    private boolean roomRequestAccepted = false;

    private final CountDownLatch serverReadyLatch = new CountDownLatch(1);
    private volatile boolean isJoining = false;

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

        // 3. Masuk ke room default
        this.activeChatRoom = new Room("general", "System", null);
        this.knownRooms.put("general", this.activeChatRoom);

        // 4. Jalankan discovery dan input user
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
                }
                System.out.println("[SYSTEM] SUCCESSOR received; downstream now " + succIp);
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
                    handleChatMessage(payload);
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

    public void handleRoomRequest(String roomName, String ip, String username) {
        Room chatRoom = knownRooms.get(roomName);
        if (chatRoom.getOwner().equals(hostIp)) {
            if (chatRoom.isBanned(ip) == true) {
                try (Socket deny = new Socket(ip, chatPort)) {
                    new DataOutputStream(deny.getOutputStream()).writeUTF("DENY_REQUEST_ROOM");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            } else {
                try (Socket accept = new Socket(ip, chatPort)) {
                    new DataOutputStream(accept.getOutputStream()).writeUTF("ACCEPT_REQUEST_ROOM");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            String packet = "JOIN_ROOM|" + roomName + "|" + ip + "|" + username;
            seenPacketIDs.add(packet);
            synchronized (clientLock) {
                chatClient.forwardPacket(packet);
            }
        }
    }

    public void handleJoinRoom(String roomname, String ip, String username) {
        Room chatRoom = knownRooms.get(roomname);
        // masukkan Ip Host dan usernamenya kedalam list user chatroom
        chatRoom.addUser(ip, username);
        System.out.printf("[SISTEM] %s(%s) telah join room %s", username, ip, roomname);
    }

    public void handleExitRoom(String roomName, String ip, String username) {
        Room chatRoom = knownRooms.get(roomName);
        chatRoom.removeUser(ip);
        System.out.printf("[SISTEM] %s(%s) telah keluar room %s", username, ip, roomName);
    }

    private void handleChatMessage(String data) {
        String[] parts = data.split("\\|", 3); // sender|roomName|content
        if (parts.length < 3)
            return;
        String sender = parts[0];
        String roomName = parts[1];
        String content = parts[2];
        if (activeChatRoom != null && activeChatRoom.getName().equals(roomName)) {
            System.out.printf("\n[%s] %s: %s\n> ", roomName, sender, content);
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

    // DEPECRATED, cuman guna kalo pake Scanner(System.in)
    private void startChatIO() {
        Scanner sc = new Scanner(System.in);
        System.out.println("\n--- P2P Chat Console ---");
        System.out.println(
                "Perintah: CREATE <room>, SEND <room> <msg>, JOIN <room>, EXIT <room>, BAN <room> <user_ip>,OFF");
        System.out.print("> ");
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            String[] parts = line.split(" ", 3);
            String command = parts[0].toUpperCase();
            switch (command) {
                case "CREATE":
                    if (parts.length > 1)
                        createRoom(parts[1]);
                    break;
                case "SEND":
                    if (parts.length > 2)
                        sendMessage(parts[1], parts[2]);
                    break;

                // udh dipindah ke joinRoom()
                case "JOIN":
                    if (parts.length > 1) {
                        Room chatRoom = knownRooms.get(parts[1]);
                        if (chatRoom != null) {
                            System.out.println("[SISTEM]Meminta permintaan owner room.....");
                            String packet = "ROOM_REQUEST|" + parts[1] + "|" + hostIp + "|" + username;
                            synchronized (clientLock) {
                                chatClient.forwardPacket(packet);
                            }
                            boolean waitingResponse = true;
                            while (waitingResponse) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }

                            if (roomRequestAccepted) {
                                this.activeChatRoom = chatRoom;
                                System.out.println("Pindah ke room: " + parts[1]);
                            } else {
                                System.out.println("Permintaan pindah ke room ditolak");
                            }
                        } else {
                            System.out.println("Room " + parts[1] + " tidak ditemukan.");
                        }
                    }
                    break;

                // udah dipindah ke banUser
                case "BAN":
                    if (parts.length > 2) {
                        Room chatRoom = knownRooms.get(parts[1]);
                        String bannedIp = parts[2];
                        if (chatRoom.getOwner().equals(hostIp)) {
                            String bannedUsername = chatRoom.banUser(bannedIp);
                            if (bannedUsername == null) {
                                System.out.printf("Tidak ada user dalam %s dengan ip tersebut\n", parts[1]);
                                continue;
                            }
                            System.out.printf("Banning %s:%s dari %s", bannedUsername, bannedIp, parts[1]);
                            String packet = "BAN_ANNOUNCE|" + parts[1] + "|" + hostIp;
                            synchronized (clientLock) {
                                chatClient.forwardPacket(packet);
                            }
                        } else {
                            System.out.println("User tidak punya hak pemilik");
                        }
                    }
                    break;

                // udah dipindah ke exitRoom
                case "EXIT":
                    if (parts.length > 1) {
                        this.activeChatRoom = null;
                        String packet = "EXIT_ROOM|" + parts[1] + "|" + hostIp + "|" + username;
                        roomRequestAccepted = false;
                        waitingRoomResponse = true;
                        synchronized (clientLock) {
                            chatClient.forwardPacket(packet);
                        }
                    }
                case "OFF":
                    System.exit(0);
                    return;
            }
            System.out.print("> ");
        }
        sc.close();
    }

    // Metode discoverAndJoin Anda yang canggih bisa diletakkan di sini.
    // Pastikan untuk menyesuaikan cara ia menggunakan Client dan Server.
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
            if (ip.equals(hostIp))
                continue;

            try (Socket s = new Socket(ip, chatPort);
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    DataInputStream in = new DataInputStream(s.getInputStream())) {

                // 1) ask to join
                out.writeUTF("JOIN_NETWORK|" + hostIp);

                // 2) get the successor back
                String resp = in.readUTF(); // "SUCCESSOR|Z"
                String succIp = resp.split("\\|", 2)[1];

                // 3) install new downstream
                synchronized (clientLock) {
                    chatClient = new Client(succIp, chatPort);
                }
                System.out.println("[SYSTEM] Joined via " + ip + "; downstream is " + succIp);
                return;

            } catch (IOException ignored) {
                // try next IP
            }
        }

        System.out.println("[SYSTEM] No peers found; starting fresh ring.");
        // chatClient already points at self
    }

    public List<Room> getRoomList() {
        return new ArrayList<Room>(knownRooms.values());
    }

    public boolean joinRoom(String roomName) {
        Room chatRoom = knownRooms.get(roomName);
        if (chatRoom != null) {
            System.out.println("[SISTEM]Meminta permintaan owner room.....");
            String packet = "ROOM_REQUEST|" + roomName + "|" + hostIp + "|" + username;
            synchronized (clientLock) {
                chatClient.forwardPacket(packet);
            }
            boolean waitingResponse = true;
            while (waitingResponse) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (roomRequestAccepted) {
                this.activeChatRoom = chatRoom;
                System.out.println("Pindah ke room: " + roomName);
                return true;
            } else {
                System.out.println("Permintaan pindah ke room ditolak");
                return false;
            }
        } else {
            System.out.println("Room " + roomName + " tidak ditemukan.");
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
        String packet = String.format("CHAT|%s|%s|%s|%s", username, roomName, message, msgId);
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

    private static String pickLocalIp() throws SocketException {
        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                    return addr.getHostAddress();
                }
            }
        }
        throw new RuntimeException("No non-loopback IPv4 found");
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
