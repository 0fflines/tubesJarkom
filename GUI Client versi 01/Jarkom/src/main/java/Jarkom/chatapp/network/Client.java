package Jarkom.chatapp.network;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Client {
    private Socket socket;
    private DataOutputStream out;
    public String destinationHost;
    public int destinationPort;

    public Client(String host, int port) {
        this.destinationHost = host;
        this.destinationPort = port;
    }

    public boolean initConnection() {
        try {
            this.socket = new Socket(destinationHost, destinationPort);
            this.out = new DataOutputStream(socket.getOutputStream());
            System.out.println("[Client] Terhubung ke peer: " + destinationHost + ":" + destinationPort);
            return true;
        } catch (IOException e) {
            System.err.println("[Client] Gagal terhubung ke peer: " + destinationHost + ":" + destinationPort);
            return false;
        }
    }

    public void forwardPacket(String packet) {
        if (out != null) {
            try {
                out.writeUTF(packet);
                out.flush();
            } catch (IOException e) {
                System.err.println("[Client] Gagal mengirim paket. Mencoba menyambung ulang...");
                initConnection(); // Coba sambung ulang jika gagal
            }
        } else {
             System.err.println("[Client] Tidak terhubung. Tidak bisa mengirim paket.");
        }
    }

    public void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("[Client] Koneksi ke " + destinationHost + " ditutup.");
            }
        } catch (IOException e) {
            // Abaikan error saat menutup
        }
    }
    
    public boolean isConnectionActive() {
        return socket != null && !socket.isClosed() && out != null;
    }
}
