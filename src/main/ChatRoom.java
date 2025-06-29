package main;

/**
 * Kelas ini merepresentasikan sebuah room chat dalam konteks P2P sederhana (berbasis konsol).
 * Fungsinya adalah sebagai penyimpan nama room dan menampilkan pesan.
 */
public class ChatRoom {

    private final String name;
    private final String owner;

    /**
     * Konstruktor untuk ChatRoom.
     * @param name Nama room.
     * @param owner Pembuat room.
     */
    public ChatRoom(String name, String owner) {
        this.name = name;
        this.owner = owner;
        System.out.println("--- Anda sekarang berada di room '" + name + "' (Owner: " + owner + ") ---");
    }

    /**
     * Mengembalikan nama dari room ini.
     * @return String nama room.
     */
    public String getName() {
        return name;
    }

    /**
     * Menampilkan pesan yang diformat ke konsol.
     * @param sender Pengirim pesan.
     * @param content Isi pesan.
     */
    public void displayMessage(String sender, String content) {
        // Langsung cetak ke konsol, sesuai dengan cara kerja Peer.java saat ini.
        System.out.println(String.format("[%s]: %s", sender, content));
    }
}
