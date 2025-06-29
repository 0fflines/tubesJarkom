package main;

import javax.swing.DefaultListModel;
import javax.swing.SwingUtilities;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Merepresentasikan state lokal dari sebuah chat room dalam arsitektur Peer-to-Peer.
 * Kelas ini mengelola data untuk ditampilkan di GUI dan melacak kapan terakhir
 * kali informasi tentang room ini diterima dari jaringan untuk mendeteksi room yang "mati".
 *
 * @author Christ
 */
public class ChatRoom {

    private final String name;
    private final String owner;

    // Timestamp terakhir kali peer ini menerima "pengumuman" (announcement) tentang room ini.
    // Krusial untuk mekanisme Time-To-Live (TTL) agar room yang ditinggalkan pemiliknya bisa hilang.
    private LocalDateTime lastAnnounced;

    // Model data untuk ditampilkan di JList/JTextArea pada GUI.
    private final DefaultListModel<String> messageModel;
    private final DefaultListModel<String> userListModel;

    // Set untuk memastikan pengguna tidak ditambahkan berulang kali ke userListModel.
    private final Set<String> detectedUsers;

    /**
     * Konstruktor untuk membuat objek ChatRoom baru ketika pertama kali "ditemukan" di jaringan.
     * @param name Nama room.
     * @param owner Username dari pembuat room.
     */
    public ChatRoom(String name, String owner) {
        this.name = name;
        this.owner = owner;
        this.lastAnnounced = LocalDateTime.now(); // Dicatat saat pertama kali dibuat/ditemukan

        this.messageModel = new DefaultListModel<>();
        this.userListModel = new DefaultListModel<>();
        this.detectedUsers = new HashSet<>();

        // Menambahkan pesan sistem saat room pertama kali muncul di daftar kita
        this.messageModel.addElement("--- Welcome to room '" + name + "' (Owner: " + owner + ") ---");
    }

    // --- GETTERS ---

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public DefaultListModel<String> getMessageModel() {
        return messageModel;
    }

    public DefaultListModel<String> getUserListModel() {
        return userListModel;
    }


    // --- PENGELOLAAN STATE ---

    /**
     * Memperbarui timestamp terakhir kali room ini diumumkan di jaringan.
     * Dipanggil setiap kali peer menerima pesan PENGUMUMAN (ANNOUNCE) untuk room ini.
     */
    public void updateLastAnnounced() {
        this.lastAnnounced = LocalDateTime.now();
    }

    /**
     * Memeriksa apakah room ini dianggap "basi" atau "mati".
     * Digunakan untuk membersihkan room dari daftar jika pemiliknya sudah tidak aktif.
     * @param timeout Durasi timeout (misalnya, 2 menit).
     * @return true jika room sudah tidak diumumkan lebih lama dari durasi timeout.
     */
    public boolean isStale(Duration timeout) {
        return Duration.between(this.lastAnnounced, LocalDateTime.now()).compareTo(timeout) > 0;
    }


    // --- INTERAKSI DENGAN GUI ---

    /**
     * Menambahkan pesan chat ke model data untuk ditampilkan di GUI.
     * Metode ini aman untuk dipanggil dari thread jaringan.
     * @param sender Pengirim pesan.
     * @param content Isi pesan.
     */
    public void addMessage(String sender, String content) {
        String formattedMessage = String.format("[%s]: %s", sender, content);
        // Menjalankan pembaruan UI di Event Dispatch Thread (EDT).
        SwingUtilities.invokeLater(() -> {
            messageModel.addElement(formattedMessage);
        });
        // Setiap ada pesan, kita anggap pengirimnya "aktif" di room ini.
        addUser(sender);
    }

    /**
     * Menambahkan pesan sistem (misal: notifikasi) ke model data GUI.
     * @param systemMessage Pesan sistem yang akan ditampilkan.
     */
    public void addSystemMessage(String systemMessage) {
        String formattedMessage = "--- " + systemMessage + " ---";
        SwingUtilities.invokeLater(() -> {
            messageModel.addElement(formattedMessage);
        });
    }

    /**
     * Menambahkan pengguna ke daftar pengguna lokal jika belum ada.
     * @param username Pengguna yang akan ditambahkan.
     */
    private void addUser(String username) {
        // HashSet.add() akan return true hanya jika elemen tersebut belum ada.
        if (detectedUsers.add(username)) {
            SwingUtilities.invokeLater(() -> {
                userListModel.addElement(username);
            });
        }
    }
}

