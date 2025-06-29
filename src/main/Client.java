package main;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Client {
    public final String destinationHost;
    public final int destinationPort;
    private DataOutputStream out;
    private Socket socket;

    public Client(String host, int port){
        this.destinationHost = host;
        this.destinationPort = port;
    }

    public void initConnection(){
        //Sampai client ketemu server, client bakal coba terus setiap 0.5 detik
        while(out == null){
            System.out.println("Trying connection...");
            try {
                System.out.println("Attempting connection to "+destinationHost+" "+destinationPort);
                Socket socket = new Socket(destinationHost, destinationPort);
                System.out.println("Client connected to "+destinationHost+"  "+destinationPort);
                out = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
    }

    public void forwardPacket(String message){
        try {
            out.writeUTF(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        try {
            out.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        out = null;
        socket = null;
    }
}
