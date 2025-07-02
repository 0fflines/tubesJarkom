package Jarkom.chatapp.models;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

// NOTE: Versi ini tidak menggunakan komponen Swing agar tetap sederhana
// dan sesuai dengan logika konsol yang ada di Peer Anda.
public class Room {
    private final String roomName;
    private final String owner;
    private LocalDateTime lastAnnounced;
    private HashMap<String, String> currUsers; // Key: IP, Value: Username
    private HashSet<String> bannedUsers;
    private LocalDateTime createdAt;
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public Room(String name, String owner) {
        this.roomName = name;
        this.owner = owner;
        this.lastAnnounced = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.currUsers = new HashMap<>();
        this.bannedUsers = new HashSet<>();
        System.out.println("--- Info: Room '" + name + "' (Owner: " + owner + ") created ---");
    }

    public String getName() { 
        return roomName; 
    }
    
    public String getOwner() { 
        return owner; 
    }

    public void updateLastAnnounced() { 
        this.lastAnnounced = LocalDateTime.now(); 
    }

    public boolean isStale(Duration timeout) {
        return Duration.between(lastAnnounced, LocalDateTime.now()).compareTo(timeout) > 0;
    }

    public void displayMessage(String sender, String content) {
        System.out.println(String.format("[%s]: %s", sender, content));
    }

    public void addUser(String ip, String username) {
        currUsers.put(ip, username);
    }

    public void removeUser(String ip) {
        currUsers.remove(ip);
    }

    public String banUser(String ip) {
        String bannedUser = currUsers.remove(ip);
        if (bannedUser != null) {
            bannedUsers.add(ip);
        }
        return bannedUser;
    }

    public boolean isBanned(String ip) {
        return bannedUsers.contains(ip);
    }

    public int getTotalMembers() {
        return currUsers.size();
    }

    public String getFormattedCreatedAt() {
        return createdAt.format(dateFormatter);
    }
    
    public Set<String> getUsers() {
        return new HashSet<>(currUsers.values());
    }
    
    // Untuk mendapatkan semua IP user (jika diperlukan)
    public Set<String> getUserIPs() {
        return currUsers.keySet();
    }
    
    // Untuk mendapatkan username berdasarkan IP
    public String getUsername(String ip) {
        return currUsers.get(ip);
    }
}
