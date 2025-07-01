/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Jarkom.chatapp.forms;

import Jarkom.chatapp.network.Peer;
import Jarkom.chatapp.models.Room;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author azrie
 */

public class RoomListForm extends JFrame {
    private String currentUser;
    private JTable roomTable;
    private DefaultTableModel tableModel;
    private JButton joinButton, createButton, refreshButton;
    private static List<Room> rooms = new ArrayList<>();


    public RoomListForm(String username) {
        this.currentUser = username;
        
        initComponents();
        loadInitialRooms();
        loadRooms();

        // Periksa apakah ada room yang sudah di-close
        if (rooms.stream().noneMatch(r -> r.getName().equalsIgnoreCase(username + "'s Room"))) {
            // Jika room sudah di-close, hapus dari list
            removeRoom(username + "'s Room");
        }
    }

    private void initComponents() {
        setTitle("Chat App - Room List (" + currentUser + ")");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel titlePanel = new JPanel();
        JLabel titleLabel = new JLabel("Available Chat Rooms");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titlePanel.add(titleLabel);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        String[] columnNames = {"Room Name", "Owner", "Users", "Created At"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        roomTable = new JTable(tableModel);
        roomTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomTable.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(roomTable);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        joinButton = new JButton("Join Room");
        createButton = new JButton("Create New Room");
        refreshButton = new JButton("Refresh List");
        buttonPanel.add(joinButton);
        buttonPanel.add(createButton);
        buttonPanel.add(refreshButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        joinButton.addActionListener(e -> joinRoom());
        createButton.addActionListener(e -> createRoom());
        refreshButton.addActionListener(e -> loadRooms());

        add(mainPanel);
    }
    
    private void loadInitialRooms() {
        if (rooms.isEmpty()) {
            // Gunakan constructor yang tepat
            rooms.add(new Room("General", "Admin", new ArrayList<>(List.of("Admin", "Alice", "Bob")), 
                       new ArrayList<>(List.of("Admin", "Alice")), "2023-05-15"));

            rooms.add(new Room("Java Programming", "Alice", new ArrayList<>(List.of("Alice", "Bob")),
                       new ArrayList<>(List.of("Alice")), "2023-05-16"));
        }
    }

    private void loadRooms() {
        tableModel.setRowCount(0);
        for (Room room : rooms) {
            Object[] rowData = {
                room.getName(),
                room.getOwner(),
                room.getTotalMembers() + " users",  // Format sederhana
                room.getFormattedCreatedAt()  // Format tanggal yang lebih baik
            };
            tableModel.addRow(rowData);
        }
    }
    
    private void joinRoom() {
        int selectedRow = roomTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, 
                "Please select a room to join", 
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String roomName = (String) tableModel.getValueAt(selectedRow, 0);
        Room selectedRoom = rooms.stream()
            .filter(r -> r.getName().equals(roomName))
            .findFirst()
            .orElse(null);

        if (selectedRoom != null) {
            // Tambahkan user ke room sebelum masuk
            selectedRoom.addMember(currentUser);
            selectedRoom.setOnline(currentUser, true);

            dispose();
            new ChatRoomForm(currentUser, selectedRoom).setVisible(true);
        }
    }

    public void addNewRoom(Room newRoom) {
        if (!roomExists(newRoom.getName())) {
            rooms.add(newRoom);
            Object[] rowData = {
                newRoom.getName(),
                newRoom.getOwner(),
                "1 user",
                newRoom.getCreatedAt()
            };
            tableModel.addRow(rowData);
        }
    }
    
    private boolean roomExists(String roomName) {
        for (Room room : rooms) {
            if (room.getName().equalsIgnoreCase(roomName)) {
                return true;
            }
        }
        return false;
    }
    
    private void createRoom() {
        CreateRoomForm createRoomForm = new CreateRoomForm(this, currentUser);
        createRoomForm.setVisible(true);
    }
    
    public static void removeRoom(String roomName) {
        rooms.removeIf(room -> room.getName().equalsIgnoreCase(roomName));
    }
    
    public void refreshRoomList() {
        loadRooms();

        // Jika implementasi socket, tambahkan:
        // broadcastRoomListUpdate();
    }
    
    public void addNewMemberToRoom(String roomName, String username) {
        Room room = findRoomByName(roomName);
        if (room != null) {
            room.addMember(username);
            room.setOnline(username, true); // Otomatis online saat pertama bergabung
        }
    }

    // Contoh mengubah status online
    public void updateUserOnlineStatus(String roomName, String username, boolean isOnline) {
        Room room = findRoomByName(roomName);
        if (room != null) {
            room.setOnline(username, isOnline);
        }
    }

    private Room findRoomByName(String roomName) {
        return rooms.stream()
            .filter(r -> r.getName().equals(roomName))
            .findFirst()
            .orElse(null);
    }
}