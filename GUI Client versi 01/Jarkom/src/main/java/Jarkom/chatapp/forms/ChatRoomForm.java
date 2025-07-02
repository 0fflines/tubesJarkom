/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Jarkom.chatapp.forms;

import Jarkom.chatapp.models.Message;
import Jarkom.chatapp.models.Room;
import Jarkom.chatapp.network.Peer;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.*;
import java.nio.file.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.Set;

public class ChatRoomForm extends JFrame {
    private Peer currentUser;
    private Room currentRoom;
    private JTextArea messageArea;
    private JButton sendButton;
    private JList<String> messageList;
    private DefaultListModel<String> messageListModel;
    private DefaultListModel<String> membersListModel;
    private JList<String> membersList;

    private final ImageIcon roomIcon = loadAndScaleIcon("/Images/room_icon.jpg", 20, 20);
    private final ImageIcon ownerIcon = loadAndScaleIcon("/Images/owner_icon.png", 20, 20);
    private final ImageIcon leaveIcon = loadAndScaleIcon("/Images/leave_icon.jpeg", 24, 24);
    private final ImageIcon closeIcon = loadAndScaleIcon("/Images/close_icon.jpg", 20, 20);
    private final ImageIcon attachIcon = loadAndScaleIcon("/Images/attach_icon.jpg", 24, 24);
    private final ImageIcon fileIcon = loadAndScaleIcon("/Images/file_icon.png", 16, 16);
    private final ImageIcon userIcon = loadAndScaleIcon("/Images/user_icon.jpg", 16, 16);
    private final ImageIcon kickIcon = loadAndScaleIcon("/Images/kick_icon.png", 16, 16);

    public ChatRoomForm(Peer peer, Room room) {
        this.currentUser = peer;
        this.currentRoom = room;
        this.currentRoom.addUser(peer.hostIp, peer.username); // Add user to room members
        this.messageListModel = new DefaultListModel<>();
        this.membersListModel = new DefaultListModel<>();

        initComponents();
        setupInfoPanel();
        setupChatPanel();
        setupInputPanel();
        setupEventHandlers();
        startMembersRefreshThread();
    }

    private ImageIcon loadAndScaleIcon(String path, int width, int height) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                byte[] bytes = is.readAllBytes();
                ImageIcon original = new ImageIcon(bytes);
                Image scaled = original.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }
            System.err.println("Icon not found: " + path);
        } catch (Exception e) {
            System.err.println("Error loading icon: " + path);
            e.printStackTrace();
        }
        return new ImageIcon();
    }

    private void setupInfoPanel() {
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        infoPanel.setBackground(new Color(240, 240, 240));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Room info
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel roomLabel = new JLabel("Room: " + currentRoom.getName(), roomIcon, SwingConstants.LEFT);
        roomLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        infoPanel.add(roomLabel, gbc);

        // Owner info
        gbc.gridy = 1;
        JLabel ownerLabel = new JLabel("Owner: " + currentRoom.getOwner(), ownerIcon, SwingConstants.LEFT);
        ownerLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        infoPanel.add(ownerLabel, gbc);

        // Members label
        gbc.gridy = 2;
        JLabel membersLabel = new JLabel("Members:", userIcon, SwingConstants.LEFT);
        membersLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        infoPanel.add(membersLabel, gbc);

        // Leave button
        gbc.gridy = 3;
        JButton leaveButton = new JButton("Leave Room", leaveIcon);
        leaveButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        leaveButton.setBackground(new Color(255, 100, 100));
        leaveButton.setForeground(Color.WHITE);
        leaveButton.setBorder(new RoundBorder(Color.WHITE, 8));
        leaveButton.addActionListener(e -> handleLeaveRoom());
        infoPanel.add(leaveButton, gbc);

        // Owner controls
        if (currentUser.equals(currentRoom.getOwner())) {
            // Close Room button
            gbc.gridy = 4;
            JButton closeButton = new JButton("Close Room", closeIcon);
            closeButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            closeButton.setBackground(new Color(255, 150, 150));
            closeButton.setForeground(Color.WHITE);
            closeButton.setBorder(new RoundBorder(Color.WHITE, 8));
            closeButton.addActionListener(e -> handleCloseRoom());
            infoPanel.add(closeButton, gbc);

            // Kick User button
            gbc.gridy = 5;
            JButton kickButton = new JButton("Kick User", kickIcon);
            kickButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            kickButton.setBackground(new Color(255, 180, 180));
            kickButton.setForeground(Color.WHITE);
            kickButton.setBorder(new RoundBorder(Color.WHITE, 8));
            kickButton.setEnabled(false);
            kickButton.addActionListener(e -> {
                String selectedUser = membersList.getSelectedValue();
                if (selectedUser != null) {
                    handleKickUser(selectedUser);
                }
            });
            infoPanel.add(kickButton, gbc);

            membersList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    String selectedUser = membersList.getSelectedValue();
                    kickButton.setEnabled(selectedUser != null && !selectedUser.equals(currentUser));
                }
            });
        }

        add(infoPanel, BorderLayout.NORTH);
    }

    private void startMembersRefreshThread() {
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Ambil daftar user terbaru dari room
                    Set<String> currentMembers = currentRoom.getUsers();

                    // Update UI di EDT (Event Dispatch Thread)
                    SwingUtilities.invokeLater(() -> {
                        membersListModel.clear();
                        for (String member : currentMembers) {
                            membersListModel.addElement(member);
                        }
                    });

                    // Tunggu 500ms sebelum refresh lagi
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void setupChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout());

        // Message list
        messageList = new JList<>(messageListModel);
        messageList.setCellRenderer(new MessageCellRenderer());
        messageList.setLayoutOrientation(JList.VERTICAL);
        messageList.setVisibleRowCount(-1);
        messageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane messageScroll = new JScrollPane(messageList);
        messageScroll.setBorder(new RoundBorder(new Color(200, 200, 200), 10));

        // Ganti user list dengan members panel
        initMembersPanel(); // Pindahkan ini ke sini

        chatPanel.add(messageScroll, BorderLayout.CENTER);
        add(chatPanel, BorderLayout.CENTER);
    }
    
    private void setupMembersPanel() {
        JPanel membersPanel = new JPanel();
        membersPanel.setLayout(new BoxLayout(membersPanel, BoxLayout.Y_AXIS));
        membersPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        membersPanel.setPreferredSize(new Dimension(200, 0));

        membersList = new JList<>(membersListModel);
        membersList.setCellRenderer(new MemberListRenderer());
        
        JScrollPane scrollPane = new JScrollPane(membersList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Members"));
        membersPanel.add(scrollPane);
        
        add(membersPanel, BorderLayout.EAST);
    }

    private void setupInputPanel() {
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        // Attach button with icon
        JButton attachButton = new JButton(attachIcon);
        attachButton.setPreferredSize(new Dimension(40, 40));
        attachButton.setToolTipText("Attach File");
        attachButton.setBorder(BorderFactory.createEmptyBorder());
        attachButton.setContentAreaFilled(false);
        attachButton.addActionListener(e -> handleAttachFile());

        // Message area
        messageArea = new JTextArea(4, 30);
        messageArea.setLineWrap(true);

        // Send button
        sendButton = new JButton("Send");

        // Layout
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(attachButton, BorderLayout.WEST);
        leftPanel.add(new JScrollPane(messageArea), BorderLayout.CENTER);

        inputPanel.add(leftPanel, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        add(inputPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        // Cari leaveButton dengan cara yang lebih reliable
        Component[] components = getContentPane().getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel) {
                Component[] subComponents = ((JPanel) comp).getComponents();
                for (Component subComp : subComponents) {
                    if (subComp instanceof JButton) {
                        JButton button = (JButton) subComp;
                        if ("Leave Room".equals(button.getText())) {
                            button.addActionListener(e -> handleLeaveRoom());
                        }
                    }
                }
            }
        }

        // Pastikan attachButton benar-benar ada
        Component southPanel = ((BorderLayout) getContentPane().getLayout()).getLayoutComponent(BorderLayout.SOUTH);
        if (southPanel instanceof JPanel) {
            Component attachPanel = ((JPanel) southPanel).getComponent(0);
            if (attachPanel instanceof JPanel) {
                Component attachButton = ((JPanel) attachPanel).getComponent(0);
                if (attachButton instanceof JButton) {
                    ((JButton) attachButton).addActionListener(e -> handleAttachFile());
                }
            }
        }

        sendButton.addActionListener(e -> handleSendMessage());

        messageArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    handleSendMessage();
                }
            }
        });
    }

    private void handleLeaveRoom() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to leave this room?",
                "Confirm Leave Room",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                addSystemMessage(currentUser + " has left the room");
                membersListModel.removeElement(currentUser);

                Timer timer = new Timer(1000, e -> {
                    this.dispose();
                    new RoomListForm(currentUser).setVisible(true);
                });
                timer.setRepeats(false);
                timer.start();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Error leaving room: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleAttachFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Attach Files");
        fileChooser.setMultiSelectionEnabled(true);

        // File filter untuk gambar
        FileNameExtensionFilter imageFilter = new FileNameExtensionFilter(
                "Images", "jpg", "jpeg", "png", "gif");
        fileChooser.addChoosableFileFilter(imageFilter);

        // Hanya tampilkan sekali
        int returnValue = fileChooser.showOpenDialog(this);

        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            if (selectedFiles.length == 0 && fileChooser.getSelectedFile() != null) {
                selectedFiles = new File[] { fileChooser.getSelectedFile() };
            }

            for (File file : selectedFiles) {
                try {
                    // Simpan file ke temp directory
                    File destDir = new File(System.getProperty("java.io.tmpdir"), "chat_files");
                    destDir.mkdirs();
                    File destFile = new File(destDir, file.getName());
                    Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    // Format pesan
                    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                    String fileInfo = String.format("[%s] [%s]", currentUser, time);

                    if (isImageFile(file.getName())) {
                        String imgMessage = String.format("%s\n[Image] %s\n%s",
                                fileInfo, file.getName(), destFile.getAbsolutePath());
                        messageListModel.addElement(imgMessage);
                    } else {
                        String fileMessage = String.format("%s\n[File] %s\n%s",
                                fileInfo, file.getName(), destFile.getAbsolutePath());
                        messageListModel.addElement(fileMessage);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            "Error attaching file: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private boolean isImageFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif");
    }

    private void handleSendMessage() {
        String text = messageArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        try {
            // Validasi koneksi peer
            if (!isPeerConnected()) {
                showConnectionError();
                return;
            }

            // Kirim pesan
            currentUser.sendMessage(currentRoom.getName(), text);

            // Tampilkan pesan lokal
            displayLocalMessage(text);

        } catch (Exception ex) {
            handleSendError(ex);
        }
    }

    private void displayLocalMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            String formattedMessage = String.format("[%s] [%s]\n%s",
                    currentUser, time, text);

            messageListModel.addElement(formattedMessage);
            messageArea.setText("");
            messageList.ensureIndexIsVisible(messageListModel.getSize() - 1);
        });
    }

    private boolean isPeerConnected() {
        return currentUser != null && currentUser.isConnected();
    }

    private void showConnectionError() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                    "Not connected to network. Message not sent.",
                    "Connection Error",
                    JOptionPane.WARNING_MESSAGE);
        });
    }

    private void handleSendError(Exception ex) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                    "Failed to send message: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        });

        // Log error untuk debugging
        System.err.println("Error sending message: " + ex.getMessage());
        ex.printStackTrace();
    }

    private void initComponents() {
        setTitle("Chat App - " + currentRoom.getName() + " (" + currentUser + ")");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        setupInfoPanel();
        setupMembersPanel();
        setupChatPanel();
        setupInputPanel();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleLeaveRoom();
            }
        });
    }

    private void initMembersPanel() {
        JPanel membersPanel = new JPanel();
        membersPanel.setLayout(new BoxLayout(membersPanel, BoxLayout.Y_AXIS));
        membersPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        membersPanel.setPreferredSize(new Dimension(200, 0));

        // Gabungkan online dan offline menjadi satu list
        DefaultListModel<String> membersListModel = new DefaultListModel<>();
        JList<String> membersList = new JList<>(membersListModel);
        membersList.setCellRenderer(new MemberListRenderer());

        JScrollPane scrollPane = new JScrollPane(membersList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Members"));

        membersPanel.add(scrollPane);
        add(membersPanel, BorderLayout.EAST);

        // Simpan reference ke model untuk diakses oleh refresh thread
        this.membersListModel = membersListModel;
        this.membersList = membersList;
    }

    private class MemberListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            // Set icon default untuk semua member
            setIcon(userIcon);
            setForeground(Color.BLACK);

            // Jika member adalah owner, beri icon khusus
            if (value.equals(currentRoom.getOwner())) {
                setIcon(ownerIcon);
                setForeground(new Color(0, 100, 0)); // Warna hijau untuk owner
            }

            return this;
        }
    }

    private void handleCloseRoom() {
        if (!currentUser.equals(currentRoom.getOwner())) {
            JOptionPane.showMessageDialog(this,
                    "Only room owner can close the room",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Closing this room will remove it and disconnect all users. Continue?",
                "Confirm Close Room",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            // 1. Kirim notifikasi ke semua pengguna
            addSystemMessage("Room closed by owner");

            // 2. Hapus room dari daftar (RoomListForm)
            RoomListForm.removeRoom(currentRoom.getName());
            notifyRoomClosed();
            // 3. Tutup form dan kembali ke room list
            dispose();
            new RoomListForm(currentUser).setVisible(true);

            // TODO: Di sini tambahkan kode untuk memutuskan semua pengguna lain via socket
        }
    }

    private void notifyRoomClosed() {
        try {
            // Kirim perintah khusus ke server
            String closureCommand = "ROOM_CLOSED:" + currentRoom.getName();
            // TODO: Implementasi pengiriman via socket
            // outToServer.println(closureCommand);

            // Untuk testing lokal:
            System.out.println("Broadcasting room closure: " + closureCommand);
        } catch (Exception e) {
            System.err.println("Error notifying room closure: " + e.getMessage());
        }
    }

    private void handleKickUser(String username) {
        if (username == null || username.equals(currentUser)) {
            JOptionPane.showMessageDialog(
                    this,
                    username == null ? "Please select a user to kick" : "You cannot kick yourself",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to kick " + username + " from this room?",
                "Confirm Kick",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            // Remove from user list
            membersListModel.removeElement(username);
            // Add system message
            addSystemMessage(username + " has been kicked by owner");

            // TODO: Add server notification for actual kick implementation
        }
    }

    public void addMessage(Message message) {
        String formattedMessage;
        if (message.getSender().equals("SYSTEM")) {
            formattedMessage = String.format("[SYSTEM] [%s]\n%s",
                    message.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")),
                    message.getContent());
        } else {
            formattedMessage = String.format("[%s] [%s]\n%s",
                    message.getSender(),
                    message.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")),
                    message.getContent());
        }

        SwingUtilities.invokeLater(() -> {
            messageListModel.addElement(formattedMessage);
            messageList.ensureIndexIsVisible(messageListModel.getSize() - 1);
        });
    }

    public void addSystemMessage(String message) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String formattedMessage = String.format("[SYSTEM] [%s]\n%s", time, message);
        SwingUtilities.invokeLater(() -> {
            messageListModel.addElement(formattedMessage);
            messageList.ensureIndexIsVisible(messageListModel.getSize() - 1);
        });
    }

    private class MessageCellRenderer extends JPanel implements ListCellRenderer<String> {
        private JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        private JLabel senderLabel = new JLabel();
        private JLabel timeLabel = new JLabel();
        private JTextArea messageArea = new JTextArea();
        private JLabel fileLabel = new JLabel();
        private JLabel imageLabel = new JLabel();

        public MessageCellRenderer() {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));
            setBackground(Color.WHITE);

            // Header Panel
            headerPanel.setOpaque(true);
            headerPanel.add(senderLabel);
            headerPanel.add(timeLabel);

            // Message Area
            messageArea.setLineWrap(true);
            messageArea.setWrapStyleWord(true);
            messageArea.setEditable(false);
            messageArea.setOpaque(true);
            messageArea.setBackground(Color.WHITE);
            messageArea.setForeground(Color.BLACK);
            messageArea.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5));

            // Content Panel
            JPanel contentPanel = new JPanel(new BorderLayout());
            contentPanel.setOpaque(true);
            contentPanel.add(messageArea, BorderLayout.CENTER);
            contentPanel.add(fileLabel, BorderLayout.WEST);
            contentPanel.add(imageLabel, BorderLayout.SOUTH);

            add(headerPanel, BorderLayout.NORTH);
            add(contentPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list,
                String value, int index, boolean isSelected, boolean cellHasFocus) {

            // Reset semua komponen
            senderLabel.setText("");
            timeLabel.setText("");
            messageArea.setText("");
            fileLabel.setIcon(null);
            imageLabel.setIcon(null);
            messageArea.setBackground(Color.WHITE);

            try {
                // Parse format: "[Sender] [Time]\nMessage" (DENGAN kurung siku)
                String[] parts = value.split("\n", 2);
                String[] headerParts = parts[0].split("\\] \\[");

                String sender = headerParts[0].substring(1); // Hilangkan '['
                String time = headerParts[1].replace("]", "");

                senderLabel.setText(sender);
                timeLabel.setText(time);

                // Handle konten pesan
                if (parts.length > 1) {
                    String content = parts[1];

                    if (content.startsWith("[Image]") && content.split("\n").length > 1) {
                        // Handle image - tanpa space kosong di atas
                        String imagePath = content.split("\n")[1].trim();
                        try {
                            ImageIcon icon = new ImageIcon(imagePath);
                            if (icon.getIconWidth() > 0) {
                                Image scaled = icon.getImage().getScaledInstance(200, 200, Image.SCALE_SMOOTH);
                                imageLabel.setIcon(new ImageIcon(scaled));
                                messageArea.setText(""); // Kosongkan text area

                                // Set background hijau muda untuk gambar jika pengirim adalah Anda
                                if (sender.equals(currentUser)) {
                                    imageLabel.setOpaque(true);
                                    imageLabel.setBackground(new Color(230, 255, 230)); // Warna hijau muda
                                } else {
                                    imageLabel.setBackground(Color.WHITE);
                                }
                            } else {
                                messageArea.setText("(Image not found: " + imagePath + ")");
                            }
                        } catch (Exception e) {
                            messageArea.setText("(Could not load image)");
                        }
                    } else {
                        // Teks biasa
                        messageArea.setText(content);
                    }

                    // Set background hijau muda untuk area pesan jika pengirim adalah Anda
                    if (sender.equals(currentUser)) {
                        messageArea.setBackground(new Color(230, 255, 230)); // Warna hijau muda
                    } else {
                        messageArea.setBackground(Color.WHITE);
                    }
                }

                // Styling yang benar
                if (sender.equals("SYSTEM")) {
                    headerPanel.setBackground(new Color(220, 240, 255)); // Biru muda
                    messageArea.setBackground(new Color(240, 248, 255));
                } else if (sender.equals(currentUser)) {
                    headerPanel.setBackground(new Color(220, 255, 220)); // Hijau muda
                    messageArea.setBackground(new Color(230, 255, 230));
                } else {
                    headerPanel.setBackground(new Color(240, 240, 240)); // Abu-abu
                    messageArea.setBackground(Color.WHITE);
                }

            } catch (Exception e) {
                messageArea.setText("Invalid message format");
            }

            return this;
        }
    }

    private static class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        private final Insets insets;

        public RoundBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
            this.insets = new Insets(radius + 2, radius + 2, radius + 2, radius + 2);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return insets;
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = this.insets.left;
            insets.top = this.insets.top;
            insets.right = this.insets.right;
            insets.bottom = this.insets.bottom;
            return insets;
        }
    }
}