package main;

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
        new Thread(() -> client.initConnections()).start();
        Scanner in = server.initConnection();

        //Thread buat baca incoming message
        new Thread(() ->{
            while (in.hasNextLine()) {
                String packet = in.nextLine();
                handleMessage(packet);
            }
            in.close();
        }).start();

        Scanner sender = new Scanner(System.in);
        while(sender.hasNextLine()){
            String msg = sender.nextLine();
            
        }
    }

    private void handleMessage(String packet) {
        String[] parts = packet.split("\\|", 3);
        String msgId = parts[0];
        String sender = parts[1];
        String content = parts[2];

        if (seenMessage.add(msgId)) {
            System.out.println(sender + ": " + content);
            client.forwardMessage(packet);
        }
    }

    public void sendMessage(String text) {
        long now = System.currentTimeMillis();
        String base  = text + now;
        String msgId = Integer.toString(base.hashCode());
        
        String packet = msgId + "|" + username + "|" + text;
        seenMessage.add(msgId);
        client.forwardMessage(packet);
    }
}
