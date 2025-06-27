package main;

import java.io.DataOutputStream;
import java.net.Socket;

public class Client {
    private final String host;
    private final int port;
    private DataOutputStream out;

    public Client(String host, int port){
        this.host = host;
        this.port = port;
    }

    public void initConnections(){
        try {
            Socket socket = new Socket(host, port);
            System.out.println("Client connected to "+host+"  "+port);
            out = new DataOutputStream(socket.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void forwardMessage(String message){
        try {
            out.writeUTF(message+"\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
