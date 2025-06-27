/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Jarkom.chatapp.forms;

import Jarkom.chatapp.models.Room;
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
    private String currentUser;

    public CreateRoomForm(RoomListForm parent, String username) {
        super(parent, "Create New Room", true);
        this.parentForm = parent;
        this.currentUser = username;
        initComponents();
    }

    private void initComponents() {
        setSize(400, 200);
        setLocationRelativeTo(getParent());
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Form panel
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 5, 10));
        
        JLabel roomNameLabel = new JLabel("Room Name:");
        roomNameField = new JTextField();
        
        formPanel.add(roomNameLabel);
        formPanel.add(roomNameField);
        
        // Empty labels for layout
        formPanel.add(new JLabel());
        formPanel.add(new JLabel());
        
        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        createButton = new JButton("Create");
        cancelButton = new JButton("Cancel");
        
        buttonPanel.add(createButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Event Handlers
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
                
                // TODO: Add create room logic with server
                // For now, just add to the parent form
                String createdAt = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                
                parentForm.addNewRoom(new Room(roomName, currentUser, 1, createdAt));
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
