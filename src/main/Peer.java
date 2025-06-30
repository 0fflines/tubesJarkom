package main;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Peer implements Server.PacketListener {
    private final String username;
    private final String hostIp;
    private final String subnet;
    private static final int chatPort = 5000;

    private final Server server;
    private Client chatClient;

    private final HashSet<String> seenPacketIDs = new HashSet<>();
    private final Map<String, ChatRoom> knownRooms = new ConcurrentHashMap<>();
    private ChatRoom activeChatRoom;
    private String lastJoinRequesterHost;

    private final Object leaveLock = new Object();
    private final Object clientLock = new Object();
    private boolean leaveConfirmed = false;

    public Peer(String username) {
        this.username = username;
        try {
            this.hostIp = InetAddress.getLocalHost().getHostAddress();
            this.subnet = hostIp.substring(0, hostIp.lastIndexOf(".") + 1);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Tidak bisa menemukan IP lokal", e);
        }

        // 1. Buat komponen Server dan daftarkan Peer ini sebagai pendengar
        this.server = new Server(chatPort);
        this.server.setPacketListener(this);
        this.server.start();

        // 2. Inisialisasi awal client (terhubung ke diri sendiri)
        this.chatClient = new Client(hostIp, chatPort);
        this.chatClient.initConnection();

        // 3. Masuk ke room default
        this.activeChatRoom = new ChatRoom("general", "System");
        this.knownRooms.put("general", this.activeChatRoom);

        // 4. Jalankan discovery dan input user
        new Thread(this::discoverAndJoin).start();
        startChatIO();
    }

    @Override
    public void onPacketReceived(String packet, DataOutputStream replyStream) {
        String[] parts = packet.split("\\|", 4);
        String type = parts[0];
        String data = parts.length > 1 ? parts[1] : "";

        // Handshake diproses secara langsung
        try {
            if ("JOIN_NETWORK".equals(type)) {
                lastJoinRequesterHost = data;
                String successorHost = (chatClient != null) ? chatClient.destinationHost : hostIp;
                replyStream.writeUTF("SUCCESSOR|" + successorHost);
                System.out.println("\n[SISTEM] Merespon permintaan JOIN dari " + data);
                return;
            } else if ("READY".equals(type)) {
                System.out.println(
                        "\n[SISTEM] Menerima sinyal READY dari " + lastJoinRequesterHost + ". Menyambung ulang...");
                chatClient.closeConnection();
                chatClient = new Client(lastJoinRequesterHost, chatPort);
                chatClient.initConnection();
                return;
            } else if ("LEAVE_NETWORK".equals(type)) {
                if (data.equals(chatClient.destinationHost))
                    leaveAck(parts[2], Integer.parseInt(parts[3]));
                else {
                    synchronized (clientLock) {
                        chatClient.forwardPacket(packet);
                    }
                }
                return;
            } else if ("LEAVE_ACK".equals(type)) {
                if (!data.equals(hostIp))
                    return;
                System.out.println("[SISTEM] Menerima ACK_LEAVE dari predecessor");
                synchronized (leaveLock) {
                    leaveConfirmed = true;
                    leaveLock.notifyAll();
                }
                return;
            }
        } catch (IOException e) {
            System.err.println("Error saat handshake: " + e.getMessage());
        }

        // Pesan gossip (CHAT, ROOM_ANNOUNCE, JOIN_ROOM, EXIT_ROOM)
        if (seenPacketIDs.add(packet)) {
            if ("CHAT".equals(type)) {
                handleChatMessage(data);
            } else if ("ROOM_ANNOUNCE".equals(type)) {
                handleRoomAnnouncement(data);
            } else if ("JOIN_ROOM".equals(type)) {
                ChatRoom room = knownRooms.get(data);
                // masukkan Ip Host dan usernamenya kedalam list user chatroom
                String joinIp = parts[2];
                String joinUsername = parts[3];
                room.addUser(joinIp, joinUsername);
                System.out.printf("[SISTEM] %s(%s) telah join room %s", joinUsername, joinIp, data);
            } else if ("EXIT_ROOM".equals(type)) {
                ChatRoom room = knownRooms.get(data);
                String joinIp = parts[2];
                String joinUsername = parts[3];
                room.removeUser(parts[2]);
                System.out.printf("[SISTEM] %s(%s) telah keluar room %s", joinUsername, joinIp, data);
            }
            // Teruskan paket gossip
            synchronized (clientLock) {
                chatClient.forwardPacket(packet);
            }
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

    public void leaveAck(String newSuccessorIP, int newSuccessorPort) {
        synchronized (clientLock) {
            String ackPacket = "LEAVE_ACK|" + chatClient.destinationHost;
            chatClient.forwardPacket(ackPacket);
            System.out.printf("[SISTEM] %s leaving; recconect to %s : %d\n", chatClient.destinationHost, newSuccessorIP,
                    newSuccessorPort);
            chatClient.closeConnection();
            chatClient = new Client(newSuccessorIP, newSuccessorPort);
            chatClient.initConnection();
        }
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

    private void handleRoomAnnouncement(String data) {
        String[] parts = data.split("\\|", 2); // roomName|owner
        if (parts.length < 2)
            return;
        String roomName = parts[0];
        String owner = parts[1];
        knownRooms.computeIfAbsent(roomName, k -> new ChatRoom(k, owner));
    }

    public void sendMessage(String roomName, String message) {
        String msgId = UUID.randomUUID().toString();
        String packet = String.format("CHAT|%s|%s|%s|%s", username, roomName, message, msgId);
        seenPacketIDs.add(packet);
        synchronized (clientLock) {
            chatClient.forwardPacket(packet);
        }
    }

    public void createRoom(String roomName) {
        if (!knownRooms.containsKey(roomName)) {
            ChatRoom newRoom = new ChatRoom(roomName, this.username);
            knownRooms.put(roomName, newRoom);
            String packet = "ROOM_ANNOUNCE|" + roomName + "|" + this.username;
            seenPacketIDs.add(packet);
            synchronized (clientLock) {
                chatClient.forwardPacket(packet);
            }
        }
    }

    private void startChatIO() {
        Scanner sc = new Scanner(System.in);
        System.out.println("\n--- P2P Chat Console ---");
        System.out.println("Perintah: CREATE <room>, SEND <room> <msg>, JOIN <room>, EXIT <room>, OFF");
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
                case "JOIN":
                    if (parts.length > 1) {
                        this.activeChatRoom = knownRooms.get(parts[1]);
                        if (this.activeChatRoom != null) {
                            System.out.println("Pindah ke room: " + parts[1]);
                            String packet = "JOIN_ROOM|" + parts[1] + "|" + hostIp + "|" + username;
                            synchronized (clientLock) {
                                chatClient.forwardPacket(packet);
                            }
                        } else {
                            System.out.println("Room " + parts[1] + " tidak ditemukan.");
                        }
                    }
                    break;
                case "EXIT":
                    if (parts.length > 1) {
                        this.activeChatRoom = null;
                        String packet = "EXIT_ROOM|" + parts[1] + "|" + hostIp + "|" + username;
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
    private void discoverAndJoin() {
        System.out.println("\n[SISTEM] Mencari peer lain di jaringan...");
        for (int i = 1; i <= 254; i++) {
            String ip = subnet + i;
            if (ip.equals(hostIp))
                continue;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, chatPort), 200);
                DataInputStream in = new DataInputStream(s.getInputStream());
                DataOutputStream out = new DataOutputStream(s.getOutputStream());

                out.writeUTF("JOIN_NETWORK|" + hostIp);

                String response = in.readUTF();
                String successorIp = response.split("\\|")[1];

                this.chatClient = new Client(successorIp, chatPort);
                this.chatClient.initConnection();

                try (Socket ack = new Socket(ip, chatPort)) {
                    new DataOutputStream(ack.getOutputStream()).writeUTF("READY");
                }
                return;
            } catch (Exception e) {
            }
        }
        // Jika sampai sini, berarti Peer ini adalah yang pertama di network
    }
}
