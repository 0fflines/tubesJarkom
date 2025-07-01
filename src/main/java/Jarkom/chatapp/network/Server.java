package Jarkom.chatapp.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private final int port;
    private PacketListener packetListener;

    // Interface ini adalah "jembatan" agar Server bisa bicara ke Peer
    public interface PacketListener {
        void onPacketReceived(String packet, DataOutputStream replyStream);
    }

    public Server(int port) {
        this.port = port;
    }

    // Peer akan memanggil ini untuk mendaftarkan dirinya sebagai pendengar
    public void setPacketListener(PacketListener listener) {
        this.packetListener = listener;
    }

    // Memulai server di thread terpisah
    public void start() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("[Server] Mendengarkan di port " + port);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    // Tangani setiap koneksi yang masuk di thread-nya sendiri agar tidak memblokir
                    new Thread(() -> handleIncomingConnection(clientSocket)).start();
                }
            } catch (IOException e) {
                System.err.println("[Server] Gagal memulai atau berhenti: " + e.getMessage());
            }
        }).start();
    }

    private void handleIncomingConnection(Socket clientSocket) {
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
            
            String packet = in.readUTF(); // Hanya baca satu paket
            
            // Jika ada yang "mendengarkan" (yaitu Peer), teruskan paketnya
            if (packetListener != null) {
                packetListener.onPacketReceived(packet, out);
            }

        } catch (IOException e) {
            // Koneksi ditutup oleh klien
        }
    }
}
