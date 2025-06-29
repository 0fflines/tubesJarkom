package main;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Scanner;

//NOTE: di function start buat ngirim message masih pake System.in

//Untuk discovery network bisa berkerja, wifi harus memiliki subnet /24
//dan semua ip dalam range yang sama (x.x.x.1 sampai x.x.x.255)

//program tidak bisa dijalankan di host yang sama (1 host 2 peer)
public class Peer {
    private String hostIp;
    private String subnet;
    private String username;
    private Server server;
    private Client chatClient;
    private HashSet<String> seenMessage = new HashSet<>();
    private String lastJoinHost;
    private static final int chatPort = 5000;

    Peer(String username){
        this.username = username;
        try {
            hostIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            hostIp = null;
        }
        //dari 192.168.1.1 menjadi 192.168.1.
        subnet = hostIp.substring(0, hostIp.lastIndexOf(".")+1);

        server = new Server(chatPort);

        server.setJoinListener((newHost, replyOut) ->{
            // stash for READY
            lastJoinHost = newHost;
            // reply with current successor
            replyOut.writeUTF("SUCCESSOR|" + chatClient.destinationHost);
        });

        server.setReadyListener(() -> {
            //tutup socket sekarang
            chatClient.closeConnection();

            // splice in the previously‑joined peer:
            chatClient = new Client(lastJoinHost, chatPort);
            chatClient.initConnection();
        });

        server.setChatListener(packet -> {
            // your existing handleMessage(packet) logic
            handleMessage(packet);
        });

        try {
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        chatClient = new Client(hostIp, chatPort);
        chatClient.initConnection();

        discoverAndJoin();
        startChatIO();
    }

    private void startChatIO() {
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            sendMessage(sc.nextLine());
        }
        sc.close();
    }

    private void handleMessage(String packet) {
        String[] parts = packet.split("\\|", 3);
        String msgId = parts[0];
        String sender = parts[1];
        String content = parts[2];

        if (seenMessage.add(msgId)) {
            System.out.println(sender + ": " + content);
            chatClient.forwardPacket(packet);
        }
    }

    //kirim message bareng IdMsg sama username pengirim
    public void sendMessage(String text) {
        long now = System.currentTimeMillis();
        String base  = text + now;
        String msgId = Integer.toString(base.hashCode());
        
        String packet = msgId + "|" + username + "|" + text+now;
        seenMessage.add(msgId);
        chatClient.forwardPacket(packet);
    }

    private void discoverAndJoin(){
        for(int i = 1; i <= 254; i++){
            String ip = subnet+i;
            if(ip.equals(hostIp)) continue;
            try(Socket s = new Socket(ip, chatPort)){
                DataInputStream in = new DataInputStream(s.getInputStream());
                DataOutputStream out = new DataOutputStream(s.getOutputStream());

                out.writeUTF("JOIN|"+hostIp);

                String response = in.readUTF();
                //--0 JOIN --1 upflow's ip --2 chatPort
                String[] responsePart = response.split("\\|");

                chatClient = new Client(responsePart[1], chatPort);
                chatClient.initConnection();

                try(Socket ack = new Socket(ip, chatPort)){
                    DataOutputStream ackOut = new DataOutputStream(ack.getOutputStream());
                    ackOut.writeUTF("READY");
                }
                return;
            } catch (Exception e) {}
        }

        //Jika sampai sini, berarti Peer ini adalah yang pertama di network
        chatClient = new Client(hostIp, chatPort);
        chatClient.initConnection();
    }
}