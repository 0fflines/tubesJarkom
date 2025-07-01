/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Jarkom.chatapp.forms;

import Jarkom.chatapp.models.Room;
import Jarkom.chatapp.network.Peer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 *
 * @author azrie
 */
public class CreateRoomForm extends JDialog {
    private JTextField roomNameField;
    private JButton createButton, cancelButton;
    private RoomListForm parentForm;
    private Peer currentUser;
    private static Room lastCreatedRoom = null;

    public CreateRoomForm(RoomListForm parent, Peer currentUser) {
        super(parent, "Create New Room", true);
        this.parentForm = parent;
        this.currentUser = currentUser;
        initComponents();
    }
    
    public static Room getLastCreatedRoom() {
        return lastCreatedRoom;
    }

    public static void clearLastCreatedRoom() {
        lastCreatedRoom = null;
    }

    private void initComponents() {
        setSize(400, 200);
        setLocationRelativeTo(getParent());
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel formPanel = new JPanel(new GridLayout(2, 2, 5, 10));
        JLabel roomNameLabel = new JLabel("Room Name:");
        roomNameField = new JTextField();
        formPanel.add(roomNameLabel);
        formPanel.add(roomNameField);
        formPanel.add(new JLabel());
        formPanel.add(new JLabel());
        mainPanel.add(formPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        createButton = new JButton("Create");
        cancelButton = new JButton("Cancel");
        buttonPanel.add(createButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        createButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String roomName = roomNameField.getText().trim();
                if (roomName.isEmpty()) {
                    JOptionPane.showMessageDialog(CreateRoomForm.this, 
                        "Room name cannot be empty", 
                        "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                String createdAt = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                
                // Buat room dengan member count 1 (hanya creator)
                parentForm.addNewRoom(roomName);
                dispose();
            }
        });
        
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        add(mainPanel);
    }
}