package main;

import java.time.Duration;
import java.time.LocalDateTime;

// NOTE: Versi ini tidak menggunakan komponen Swing agar tetap sederhana
// dan sesuai dengan logika konsol yang ada di Peer Anda.
public class ChatRoom {
    private final String name;
    private final String owner;
    private LocalDateTime lastAnnounced;

    public ChatRoom(String name, String owner) {
        this.name = name;
        this.owner = owner;
        this.lastAnnounced = LocalDateTime.now();
        System.out.println("--- Info: Room '" + name + "' (Owner: " + owner + ") terdeteksi/dibuat. ---");
    }

    public String getName() { return name; }
    public String getOwner() { return owner; }

    public void updateLastAnnounced() { this.lastAnnounced = LocalDateTime.now(); }

    public boolean isStale(Duration timeout) {
        return Duration.between(lastAnnounced, LocalDateTime.now()).compareTo(timeout) > 0;
    }

    public void displayMessage(String sender, String content) {
        System.out.println(String.format("[%s]: %s", sender, content));
    }
}
