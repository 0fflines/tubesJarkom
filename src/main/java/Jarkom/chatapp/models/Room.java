package Jarkom.chatapp.models;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

// NOTE: Versi ini tidak menggunakan komponen Swing agar tetap sederhana
// dan sesuai dengan logika konsol yang ada di Peer Anda.
public class Room {
    private final String roomName;
    private final String ownerIp;
    private LocalDateTime lastAnnounced;
    private HashMap<String, String> currUsers;
    private HashSet<String> bannedUsers;
    private String createdAt;
    private final String ownerName;

    public Room(String name, String ownerIp, String createdAt, String ownerName) {
        this.roomName = name;
        this.ownerIp = ownerIp;
        this.lastAnnounced = LocalDateTime.now();
        this.ownerName = ownerName;
        this.createdAt = createdAt;
        currUsers = new HashMap<>();
        bannedUsers = new HashSet<>();
        System.out.println("--- Info: Room '" + name + "' (Owner: " + ownerIp + ") terdeteksi/dibuat. ---");
    }

    public String getName() { return roomName; }
    public String getOwner() { return ownerIp; }

    public void updateLastAnnounced() { this.lastAnnounced = LocalDateTime.now(); }

    public boolean isStale(Duration timeout) {
        return Duration.between(lastAnnounced, LocalDateTime.now()).compareTo(timeout) > 0;
    }

    public void displayMessage(String sender, String content) {
        System.out.println(String.format("[%s]: %s", sender, content));
    }

    public void addUser(String ip, String username){
        currUsers.put(ip, username);
    }

    public void removeUser(String ip){
        currUsers.remove(ip);
    }

    public String banUser(String ip){
        String bannedUser = currUsers.remove(ip);
        if(bannedUser == null){
            return null;
        }
        bannedUsers.add(ip);
        return bannedUser;
    }

    public boolean isBanned(String ip){
        return bannedUsers.contains(ip);
    }

    public int getTotalMembers(){
        return currUsers.size();
    }

    public String getFormattedCreatedAt(){
        return createdAt;
    }

    public String getOwnerName(){
        return ownerName;
    }
    
    public Set<String> getUsers() {
        return new HashSet<>(this.currUsers.values());
    }
}