package Jarkom.chatapp.network;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
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
            boolean isOpen = isPortOpen(destinationHost, destinationPort);
            System.out.println("IS PORT OPEN:+" + isOpen);
            this.socket = new Socket(destinationHost, destinationPort);
            this.out = new DataOutputStream(socket.getOutputStream());
            System.out.println("[Client] Terhubung ke peer: " + destinationHost + ":" + destinationPort);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[Client] Gagal terhubung ke peer: " + destinationHost + ":" + destinationPort);
            return false;
        }
    }

    public void forwardPacket(String packet) {
        System.out.println("[Client] Opening new socket to "
                + destinationHost + ":" + destinationPort
                + " → sending “" + packet + "”");
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(destinationHost, destinationPort), 500);
            try (DataOutputStream out = new DataOutputStream(sock.getOutputStream())) {
                out.writeUTF(packet);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("[Client] Gagal mengirim paket ke "
                    + destinationHost + ":" + destinationPort
                    + " — " + e.getMessage());
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

    public boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
