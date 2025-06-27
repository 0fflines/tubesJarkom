/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Jarkom.chatapp.forms;

import Jarkom.chatapp.models.Message;
import Jarkom.chatapp.models.Room;
import Jarkom.chatapp.models.User;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
/**
 *
 * @author azrie
 */

public class ChatRoomForm extends JFrame {
    private String currentUser;
    private Room currentRoom;
    private DefaultListModel<String> userListModel;
    private DefaultListModel<String> messageListModel;
    
    // Komponen yang disimpan sebagai field
    private JTextArea messageArea;
    private JButton sendButton;
    private JList<String> messageList;
    private JScrollPane messageScrollPane;

    public ChatRoomForm(String username, Room room) {
        this.currentUser = username;
        this.currentRoom = room;
        this.userListModel = new DefaultListModel<>();
        this.messageListModel = new DefaultListModel<>();
        initComponents();
        loadInitialData();
    }

    private void initComponents() {
        setTitle("Chat App - " + currentRoom.getName() + " (" + currentUser + ")");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        // Main panel dengan BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 1. Panel Info (Atas)
        JPanel infoPanel = createInfoPanel();
        mainPanel.add(infoPanel, BorderLayout.NORTH);

        // 2. Panel Chat (Tengah)
        JPanel chatPanel = createChatPanel();
        mainPanel.add(chatPanel, BorderLayout.CENTER);

        // 3. Panel Input (Bawah)
        JPanel inputPanel = createInputPanel();
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        // Window Listener untuk handle window closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleLeaveRoom();
            }
        });

        add(mainPanel);
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JLabel roomLabel = new JLabel("Room: " + currentRoom.getName());
        roomLabel.setFont(new Font("Arial", Font.BOLD, 16));
        panel.add(roomLabel);
        
        JLabel ownerLabel = new JLabel("Owner: " + currentRoom.getOwner());
        panel.add(ownerLabel);
        
        JButton leaveButton = new JButton("Leave Room");
        leaveButton.addActionListener(e -> handleLeaveRoom());
        panel.add(leaveButton);
        
        if (currentUser.equals(currentRoom.getOwner())) {
            JButton closeRoomButton = new JButton("Close Room");
            closeRoomButton.addActionListener(e -> handleCloseRoom());
            panel.add(closeRoomButton);
        }
        
        return panel;
    }

    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Message List
        messageList = new JList<>(messageListModel);
        messageList.setCellRenderer(new MessageCellRenderer());
        messageScrollPane = new JScrollPane(messageList);
        panel.add(messageScrollPane, BorderLayout.CENTER);
        
        // User List
        panel.add(createUserListPanel(), BorderLayout.EAST);
        
        return panel;
    }

    private JPanel createUserListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Online Users"));
        panel.setPreferredSize(new Dimension(150, 0));
        
        JList<String> userList = new JList<>(userListModel);
        JScrollPane userScrollPane = new JScrollPane(userList);
        panel.add(userScrollPane, BorderLayout.CENTER);
        
        if (currentUser.equals(currentRoom.getOwner())) {
            JButton kickButton = new JButton("Kick User");
            kickButton.addActionListener(e -> handleKickUser(userList));
            panel.add(kickButton, BorderLayout.SOUTH);
        }
        
        return panel;
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        
        messageArea = new JTextArea(3, 50);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        JScrollPane inputScrollPane = new JScrollPane(messageArea);
        
        sendButton = new JButton("Send");
        sendButton.setPreferredSize(new Dimension(80, 50));
        sendButton.addActionListener(e -> handleSendMessage());
        
        // Key Listener untuk handle Enter key
        messageArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    handleSendMessage();
                }
            }
        });
        
        panel.add(inputScrollPane, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);
        
        return panel;
    }

    private void handleSendMessage() {
        String text = messageArea.getText().trim();
        if (!text.isEmpty()) {
            // TODO: Kirim pesan ke server
            addMessage(new Message(currentUser, text, LocalDateTime.now()));
            messageArea.setText("");
        }
        messageArea.requestFocusInWindow();
    }

    private void handleLeaveRoom() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to leave this room?",
            "Confirm Leave", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            // TODO: Handle leave room dengan server
            dispose();
            new RoomListForm(currentUser).setVisible(true);
        }
    }

    private void handleCloseRoom() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to close this room?",
            "Confirm Close Room", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            // TODO: Handle close room dengan server
            dispose();
            new RoomListForm(currentUser).setVisible(true);
        }
    }

    private void handleKickUser(JList<String> userList) {
        String selectedUser = userList.getSelectedValue();
        if (selectedUser == null || selectedUser.equals(currentUser)) {
            JOptionPane.showMessageDialog(this,
                selectedUser == null ? "Please select a user to kick" : "You cannot kick yourself",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to kick " + selectedUser + "?",
            "Confirm Kick", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            // TODO: Handle kick user dengan server
            userListModel.removeElement(selectedUser);
            addSystemMessage(selectedUser + " has been kicked from the room");
        }
    }

    public void addMessage(Message message) {
        String formattedMessage = String.format("[%s] %s: %s",
            message.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")),
            message.getSender(),
            message.getContent());
        
        SwingUtilities.invokeLater(() -> {
            messageListModel.addElement(formattedMessage);
            messageList.ensureIndexIsVisible(messageListModel.getSize() - 1);
        });
    }

    public void addSystemMessage(String message) {
        addMessage(new Message("SYSTEM", message, LocalDateTime.now()));
    }

    private void loadInitialData() {
        // TODO: Ganti dengan data dari server
        // Contoh data dummy
        userListModel.addElement(currentUser);
        userListModel.addElement("Alice");
        userListModel.addElement("Bob");
        
        addSystemMessage("Welcome to " + currentRoom.getName() + "!");
        addSystemMessage("You joined the room");
        
        addMessage(new Message("Alice", "Hi everyone!", LocalDateTime.now().minusMinutes(10)));
        addMessage(new Message("Bob", "Hello Alice!", LocalDateTime.now().minusMinutes(9)));
    }

    private class MessageCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, 
                int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            String message = (String) value;
            if (message.startsWith("[" + currentUser + "]")) {
                c.setForeground(new Color(0, 100, 0)); // Hijau gelap untuk pesan sendiri
                c.setFont(c.getFont().deriveFont(Font.BOLD));
            } else if (message.startsWith("[SYSTEM]")) {
                c.setForeground(Color.BLUE);
                c.setFont(c.getFont().deriveFont(Font.ITALIC));
            }
            
            return c;
        }
    }
}