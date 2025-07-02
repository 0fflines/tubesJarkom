package Jarkom.chatapp.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private final int port;
    private PacketListener packetListener;
    private Peer peer;

    // Interface ini adalah "jembatan" agar Server bisa bicara ke Peer
    public interface PacketListener {
        void onPacketReceived(String packet, DataOutputStream replyStream);
    }

    public Server(int port, Peer peer) {
        this.port = port;
        this.peer = peer;
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
                peer.signalServerReady();
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
        // 1) Log setiap koneksi TCP
        System.out.println("[Server] got connection from " + clientSocket.getRemoteSocketAddress());
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            // 2) Baca tepat satu paket saja
            String packet = in.readUTF();

            // 3) Proses paket
            packetListener.onPacketReceived(packet, out);

            // 4) Flush reply (jika ada) dan tutup socket agar caller bisa lanjut
            out.flush();
            clientSocket.close();

        } catch (EOFException eof) {
            // normal termination
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}