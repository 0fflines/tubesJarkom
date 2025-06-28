package main;

import java.io.DataInputStream;
import java.util.HashSet;
import java.util.Scanner;

//NOTE: di function start buat ngirim message masih pake System.in
public class Peer {
    private String username;
    private Server server;
    private Client client;
    private HashSet<String> seenMessage = new HashSet<>();

    Peer(String username, int myPort, String nextHost, int nextPort){
        this.username = username;
        server = new Server(myPort);
        client = new Client(nextHost, nextPort);
        start();
    }

    public void start(){
        //initConnection Client/Server perlu ada di thread salah satu soalnya client sama server harus dimulai barengan
        new Thread(() -> client.initConnection()).start();
        DataInputStream in = server.initConnection();

        //Thread buat baca incoming message
        new Thread(() ->{
            while (true) {
                try {
                    String packet = in.readUTF();
                    handleMessage(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        Scanner sender = new Scanner(System.in);
        while(sender.hasNextLine()){
            String msg = sender.nextLine();
            sendMessage(msg);
        }
    }

    private void handleMessage(String packet) {
        String[] parts = packet.split("\\|", 3);
        String msgId = parts[0];
        String sender = parts[1];
        String content = parts[2];

        if (seenMessage.add(msgId)) {
            System.out.println(sender + ": " + content);
            client.forwardPacket(packet);
        }
    }

    //kirim message bareng IdMsg sama username pengirim
    public void sendMessage(String text) {
        long now = System.currentTimeMillis();
        String base  = text + now;
        String msgId = Integer.toString(base.hashCode());
        
        String packet = msgId + "|" + username + "|" + text+now;
        seenMessage.add(msgId);
        client.forwardPacket(packet);
    }
}
