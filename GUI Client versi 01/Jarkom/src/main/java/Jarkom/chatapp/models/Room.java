/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Jarkom.chatapp.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

/**
 *
 * @author azrie
 */

public class Room {
    private String name;
    private String owner;
    private List<String> members = new ArrayList<>();      // Inisialisasi langsung
    private List<String> onlineMembers = new ArrayList<>(); // Inisialisasi langsung
    private String createdAt;

    // Constructor 1
    public Room(String name, String owner) {
        this(name, owner, new ArrayList<>(), new ArrayList<>(), "");
    }

    // Constructor 2 (perbaiki yang ini)
    public Room(String name, String owner, int totalMembers, String createdAt) {
        this.name = name;
        this.owner = owner;
        this.members = new ArrayList<>();
        this.onlineMembers = new ArrayList<>();
        this.createdAt = createdAt;
        
        // Tambahkan owner sebagai member pertama
        this.members.add(owner);
    }

    // Constructor 3
    public Room(String name, String owner, List<String> members, 
               List<String> onlineMembers, String createdAt) {
        this.name = name;
        this.owner = owner;
        this.members = new ArrayList<>(members);
        this.onlineMembers = new ArrayList<>(onlineMembers);
        this.createdAt = createdAt;
    }

    public void addMember(String username) {
        if (!members.contains(username)) {
            members.add(username);
        }
    }
    
    public void removeMember(String username) {
        members.remove(username);
        onlineMembers.remove(username);
    }
    
    // Getter methods
    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public int getTotalMembers() {
        return members.size();
    }

    public String getCreatedAt() {
        return createdAt;
    }
    
    // Getter methods
    public List<String> getMembers() {
        return new ArrayList<>(members); // Return copy untuk menghindari modifikasi langsung
    }

    public List<String> getOnlineMembers() {
        return new ArrayList<>(onlineMembers);
    }

    public int getOnlineCount() {
        return onlineMembers.size();
    }

    // Formatted date for display
    public String getFormattedCreatedAt() {
        try {
            LocalDateTime date = LocalDateTime.parse(createdAt, 
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));
        } catch (Exception e) {
            return createdAt;
        }
    }
    
    // Setter methods (jika diperlukan)
    public void setName(String name) {
        this.name = name;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setOnline(String username, boolean isOnline) {
        if (isOnline) {
            if (!onlineMembers.contains(username)) {
                onlineMembers.add(username);
            }
            if (!members.contains(username)) {
                members.add(username); // Otomatis jadi member jika online
            }
        } else {
            onlineMembers.remove(username);
        }
    }
}
