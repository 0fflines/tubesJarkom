package main;

import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class P2P_NetworkManager implements Runnable {
    // Konfigurasi Jaringan
    private final String username;
    private final int myPort;
    private final String nextPeerHost;
    private final int nextPeerPort;

    // Komponen Jaringan
    private ServerSocket serverSocket;
    private Socket nextPeerSocket;
    private DataOutputStream outToNextPeer;
    private final HashSet<String> seenMessageIDs = new HashSet<>();

    // State Aplikasi
    private final Map<String, ChatRoom> knownRooms = new ConcurrentHashMap<>();
    private final P2P_Callbacks guiCallback;

    // Scheduler untuk tugas periodik (mengumumkan room & membersihkan room mati)
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public P2P_NetworkManager(String username, int myPort, String nextPeerHost, int nextPeerPort, P2P_Callbacks callback) {
        this.username = username;
        this.myPort = myPort;
        this.nextPeerHost = nextPeerHost;
        this.nextPeerPort = nextPeerPort;
        this.guiCallback = callback;
    }

    @Override
    public void run() {
        // Mulai scheduler untuk tugas-tugas background
        startPeriodicTasks();

        // Coba terhubung ke peer berikutnya
        connectToNextPeer();

        // Mulai mendengarkan koneksi dari peer sebelumnya
        listenForPreviousPeer();
    }

    private void connectToNextPeer() {
        try {
            this.nextPeerSocket = new Socket(nextPeerHost, nextPeerPort);
            this.outToNextPeer = new DataOutputStream(nextPeerSocket.getOutputStream());
            guiCallback.onConnectionStatusChanged(true);
            guiCallback.onSystemMessage("Successfully connected to next peer at " + nextPeerHost + ":" + nextPeerPort);
        } catch (IOException e) {
            guiCallback.onConnectionStatusChanged(false);
            guiCallback.onSystemMessage("Error: Could not connect to next peer. Please check host/port and ensure the next peer is running.");
        }
    }

    private void listenForPreviousPeer() {
        try {
            serverSocket = new ServerSocket(myPort);
            guiCallback.onSystemMessage("Listening for incoming connection on port " + myPort);
            Socket previousPeerSocket = serverSocket.accept();
            guiCallback.onSystemMessage("Peer connected from " + previousPeerSocket.getInetAddress());

            DataInputStream inFromPreviousPeer = new DataInputStream(previousPeerSocket.getInputStream());
            while (true) {
                String packet = inFromPreviousPeer.readUTF();
                handleIncomingPacket(packet);
            }
        } catch (IOException e) {
            guiCallback.onSystemMessage("Connection from previous peer lost.");
        }
    }
    
    private void handleIncomingPacket(String packet) {
        // Mencegah pesan berputar selamanya
        if (seenMessageIDs.add(packet)) { 
            String[] parts = packet.split("\\|", 2);
            String type = parts[0];
            String data = parts.length > 1 ? parts[1] : "";

            if ("CHAT".equals(type)) {
                String[] chatParts = data.split("\\|", 3); // sender|roomName|content
                if (chatParts.length == 3) {
                    guiCallback.onMessageReceived(chatParts[1], chatParts[0], chatParts[2]);
                }
            } else if ("ROOM_ANNOUNCE".equals(type)) {
                String[] roomParts = data.split("\\|", 2); // roomName|owner
                 if (roomParts.length == 2) {
                    ChatRoom room = knownRooms.computeIfAbsent(roomParts[0], k -> new ChatRoom(roomParts[0], roomParts[1]));
                    room.updateLastAnnounced();
                    updateGuiRoomList();
                }
            }
            // Forward paket ke peer berikutnya
            forwardPacket(packet);
        }
    }

    private synchronized void forwardPacket(String packet) {
        if (outToNextPeer != null) {
            try {
                outToNextPeer.writeUTF(packet);
                outToNextPeer.flush();
            } catch (IOException e) {
                guiCallback.onSystemMessage("Error: Failed to forward packet. Connection to next peer may be lost.");
            }
        }
    }

    // Metode yang dipanggil oleh GUI
    public void sendMessage(String roomName, String message) {
        String packet = String.format("CHAT|%s|%s|%s", this.username, roomName, message);
        seenMessageIDs.add(packet); // Tambahkan pesan sendiri agar tidak diproses lagi
        forwardPacket(packet);
    }

    public void createAndAnnounceRoom(String roomName) {
        ChatRoom newRoom = new ChatRoom(roomName, this.username);
        knownRooms.put(roomName, newRoom);
        updateGuiRoomList();
        // Langsung umumkan room baru ini
        announceOwnedRooms();
    }

    // Tugas-tugas periodik
    private void startPeriodicTasks() {
        // Setiap 30 detik, umumkan room yang kita buat
        scheduler.scheduleAtFixedRate(this::announceOwnedRooms, 0, 30, TimeUnit.SECONDS);
        // Setiap 1 menit, bersihkan room yang sudah tidak aktif
        scheduler.scheduleAtFixedRate(this::purgeStaleRooms, 1, 1, TimeUnit.MINUTES);
    }
    
    private void announceOwnedRooms() {
        for (ChatRoom room : knownRooms.values()) {
            if (room.getOwner().equals(this.username)) {
                String packet = String.format("ROOM_ANNOUNCE|%s|%s", room.getName(), room.getOwner());
                forwardPacket(packet);
            }
        }
    }
    
    private void purgeStaleRooms() {
        boolean changed = knownRooms.values().removeIf(room -> room.isStale(Duration.ofMinutes(2)));
        if (changed) {
            updateGuiRoomList();
        }
    }

    private void updateGuiRoomList() {
        List<String> roomNames = new ArrayList<>(knownRooms.keySet());
        Collections.sort(roomNames);
        guiCallback.onRoomListUpdated(roomNames);
    }
}
