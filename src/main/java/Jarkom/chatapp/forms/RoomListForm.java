/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Jarkom.chatapp.forms;

import Jarkom.chatapp.models.Room;
import Jarkom.chatapp.network.Peer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author azrie
 */

// PERLU WINDOW BARU
// 1. Nunggu request join room
// 2. User telah diban
// 3. Sedang disconnect dari network waktu tutup network
// 4. Request join room gagal/user di ban jadi g bisa masuk

public class RoomListForm extends JFrame {
    private Peer currentUser;
    private JTable roomTable;
    private DefaultTableModel tableModel;
    private JButton joinButton, createButton, refreshButton;
    private static List<Room> rooms = new ArrayList<>();

    public RoomListForm(Peer peer) {
        this.currentUser = peer;
        initComponents();
        loadRooms();

        // Periksa apakah ada room yang sudah di-close
        if (rooms.stream().noneMatch(r -> r.getName().equalsIgnoreCase(currentUser.username + "'s Room"))) {
            // Jika room sudah di-close, hapus dari list
            removeRoom(currentUser.username + "'s Room");
        }

        // refresh listRoom setiap 3 detik
        new Thread(this::refreshRoomList).start();
    }

    private void initComponents() {
        setTitle("Chat App - Room List (" + currentUser + ")");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);

        // override fungsi exit
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent evt) {
                currentUser.leaveNetwork();
                dispose();
                System.exit(0);
            }
        });

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel titlePanel = new JPanel();
        JLabel titleLabel = new JLabel("Available Chat Rooms");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titlePanel.add(titleLabel);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        String[] columnNames = { "Room Name", "Owner", "Users", "Created At" };
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

    private void loadRooms() {
        tableModel.setRowCount(0);
        for (Room room : currentUser.getRoomList()) {
            Object[] rowData = {
                    room.getName(),
                    room.getOwnerName()+"("+room.getOwner()+")",
                    room.getTotalMembers() + " users", // Format sederhana
                    room.getFormattedCreatedAt() // Format tanggal yang lebih baik
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
        // Grab from the live list instead of the static one
        Room selectedRoom = currentUser.getRoomList().stream()
                .filter(r -> r.getName().equals(roomName))
                .findFirst()
                .orElse(null);

        if (selectedRoom != null) {
            // Send join request; joinRoom returns false if denied
            if (!currentUser.joinRoom(roomName)) {
                JOptionPane.showMessageDialog(this,
                        "Join request was denied or failed.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            currentUser.activeChatRoom = selectedRoom;
            dispose();
            new ChatRoomForm(currentUser, selectedRoom).setVisible(true);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Selected room not found.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void addNewRoom(String roomName) {
        currentUser.createRoom(roomName);
        loadRooms();
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
        while (true) {
            loadRooms();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Room findRoomByName(String roomName) {
        return rooms.stream()
                .filter(r -> r.getName().equals(roomName))
                .findFirst()
                .orElse(null);
    }
}