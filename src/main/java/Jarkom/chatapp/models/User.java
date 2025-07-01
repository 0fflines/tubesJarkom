/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Jarkom.chatapp.models;

/**
 *
 * @author azrie
 */
public class User {
    private String username;
    private boolean online;
    
    public User(String username, boolean online) {
        this.username = username;
        this.online = online;
    }
    
    // Getters and setters
    public String getUsername() { return username; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
}
