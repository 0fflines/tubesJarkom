/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Jarkom.chatapp.forms;

import Jarkom.chatapp.models.Room;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

    public RoomListForm(String username) {
        this.currentUser = username;
        initComponents();
        loadRooms(); // Load initial room list
    }

    private void initComponents() {
        setTitle("Chat App - Room List (" + currentUser + ")");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Title Panel
        JPanel titlePanel = new JPanel();
        JLabel titleLabel = new JLabel("Available Chat Rooms");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titlePanel.add(titleLabel);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        // Room Table
        String[] columnNames = {"Room Name", "Owner", "Users", "Created At"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table non-editable
            }
        };
        roomTable = new JTable(tableModel);
        roomTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomTable.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(roomTable);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        joinButton = new JButton("Join Room");
        createButton = new JButton("Create New Room");
        refreshButton = new JButton("Refresh List");

        buttonPanel.add(joinButton);
        buttonPanel.add(createButton);
        buttonPanel.add(refreshButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Event Handlers
        joinButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedRow = roomTable.getSelectedRow();
                if (selectedRow == -1) {
                    JOptionPane.showMessageDialog(RoomListForm.this, 
                        "Please select a room to join", 
                        "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String roomName = (String) tableModel.getValueAt(selectedRow, 0);
                String owner = (String) tableModel.getValueAt(selectedRow, 1);
                
                // TODO: Add join room logic with server
                dispose();
                new ChatRoomForm(currentUser, new Room(roomName, owner)).setVisible(true);
            }
        });

        createButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                CreateRoomForm createRoomForm = new CreateRoomForm(RoomListForm.this, currentUser);
                createRoomForm.setVisible(true);
            }
        });

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadRooms();
            }
        });

        add(mainPanel);
    }

    private void loadRooms() {
        // Clear existing data
        tableModel.setRowCount(0);

        // TODO: Replace with actual data from server
        // Mock data for demonstration
        List<Room> rooms = new ArrayList<>();
        rooms.add(new Room("General", "Admin", 5, "2023-05-15"));
        rooms.add(new Room("Java Programming", "Alice", 3, "2023-05-16"));
        rooms.add(new Room("Project Discussion", "Bob", 2, "2023-05-17"));

        for (Room room : rooms) {
            Object[] rowData = {
                room.getName(),
                room.getOwner(),
                room.getUserCount() + " users",
                room.getCreatedAt()
            };
            tableModel.addRow(rowData);
        }
    }

    public void addNewRoom(Room newRoom) {
        Object[] rowData = {
            newRoom.getName(),
            newRoom.getOwner(),
            "1 user", // Just created, only owner is present
            newRoom.getCreatedAt()
        };
        tableModel.addRow(rowData);
    }
}
