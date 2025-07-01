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
        this.activeChatRoom = new Room("general", "System", null);
        this.knownRooms.put("general", this.activeChatRoom);

        // 4. Jalankan discovery dan input user
        new Thread(this::discoverAndJoin).start();
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
            } else if ("REQUEST_ROOM".equals(type)) {
                handleRoomRequest(data, parts[2], parts[3]);
                return;
            } else if ("DENY_REQUEST_ROOM".equals(type)) {
                waitingRoomResponse = false;
            } else if ("ACCEPT_REQUEST_ROOM".equals(type)) {
                waitingRoomResponse = false;
                roomRequestAccepted = true;
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
                // roomname, ip, username
                handleJoinRoom(data, parts[2], parts[3]);
            } else if ("EXIT_ROOM".equals(type)) {
                // roomname, ip, username
                handleExitRoom(data, parts[2], parts[3]);
            } else if ("BAN_ANNOUNCE".equals(type)) {
                handleBanAnnounce(parts[1], parts[2]);
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

    private void handleRoomAnnouncement(String data) {
        String[] parts = data.split("\\|", 3); // roomName|owner
        if (parts.length < 3)
            return;
        String roomName = parts[0];
        String owner = parts[1];
        String date = parts[2];
        knownRooms.computeIfAbsent(roomName, k -> new Room(k, owner, date));
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
    private void discoverAndJoin() {
        System.out.println("\n[SISTEM] Mencari peer lain di jaringan...");
        for (int i = 1; i <= 254; i++) {
            String ip = subnet + i;
            if (ip.equals(hostIp))
                continue;
            try (Socket s = new Socket()) {
                boolean connected = false;
                for (int attempt = 0; attempt < 5 && !connected; attempt++) {
                    try {
                        s.connect(new InetSocketAddress(ip, chatPort), 200);
                        connected = true;
                    } catch (IOException e) {
                        Thread.sleep(100); // wait and retry
                    }
                }
                if (!connected)
                    continue; // give up on this peer
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
            String packet = "ROOM_ANNOUNCE|" + roomName + "|" + this.hostIp + "|" + LocalDate.now().toString();
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
}
