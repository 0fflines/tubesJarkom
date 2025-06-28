package main;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Client {
    private final String host;
    private final int port;
    private DataOutputStream out;

    public Client(String host, int port){
        this.host = host;
        this.port = port;
    }

    public void initConnection(){
        //Sampai client ketemu server, client bakal coba terus setiap 0.5 detik
        while(out == null){
            System.out.println("Trying connection...");
            try {
                System.out.println("Attempting connection to "+host+" "+port);
                Socket socket = new Socket(host, port);
                System.out.println("Client connected to "+host+"  "+port);
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
}
