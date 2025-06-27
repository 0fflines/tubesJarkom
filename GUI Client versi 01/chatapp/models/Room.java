/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Jarkom.chatapp.models;

/**
 *
 * @author azrie
 */
public class Room {
    private String name;
    private String owner;
    private int userCount;
    private String createdAt;

    public Room(String name, String owner) {
        this(name, owner, 0, "");
    }

    public Room(String name, String owner, int userCount, String createdAt) {
        this.name = name;
        this.owner = owner;
        this.userCount = userCount;
        this.createdAt = createdAt;
    }

    // Getters and setters
    public String getName() { return name; }
    public String getOwner() { return owner; }
    public int getUserCount() { return userCount; }
    public String getCreatedAt() { return createdAt; }
}
