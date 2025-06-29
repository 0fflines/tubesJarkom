package main;

import javax.swing.DefaultListModel;
import javax.swing.SwingUtilities;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class ChatRoom {
    private final String name;
    private final String owner;
    private LocalDateTime lastAnnounced;
    private final DefaultListModel<String> messageModel;
    private final DefaultListModel<String> userListModel;
    private final Set<String> detectedUsers;

    public ChatRoom(String name, String owner) {
        this.name = name;
        this.owner = owner;
        this.lastAnnounced = LocalDateTime.now();
        this.messageModel = new DefaultListModel<>();
        this.userListModel = new DefaultListModel<>();
        this.detectedUsers = new HashSet<>();
        this.messageModel.addElement("--- Welcome to room '" + name + "' (Owner: " + owner + ") ---");
        addUser(owner); // Tambahkan pemilik sebagai pengguna pertama
    }

    public String getName() { return name; }
    public String getOwner() { return owner; }
    public DefaultListModel<String> getMessageModel() { return messageModel; }
    public DefaultListModel<String> getUserListModel() { return userListModel; }

    public void updateLastAnnounced() {
        this.lastAnnounced = LocalDateTime.now();
    }

    public boolean isStale(Duration timeout) {
        return Duration.between(this.lastAnnounced, LocalDateTime.now()).compareTo(timeout) > 0;
    }

    public void addMessage(String sender, String content) {
        String formattedMessage = String.format("[%s]: %s", sender, content);
        SwingUtilities.invokeLater(() -> messageModel.addElement(formattedMessage));
        addUser(sender);
    }

    private void addUser(String username) {
        if (detectedUsers.add(username)) {
            SwingUtilities.invokeLater(() -> userListModel.addElement(username));
        }
    }
}
