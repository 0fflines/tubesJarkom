package main;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public interface JoinListener {
        /** Called when a JOIN arrives. 
            @param newHost the joiner’s IP 
            @param replyStream an open DataOutputStream you can use to send the SUCCESSOR response 
        */
        void onJoin(String newHost, DataOutputStream replyStream) throws IOException;
    }

    public interface ReadyListener {
        /** Called when a READY arrives. */
        void onReady();
    }

    public interface ChatListener {
        /** Called when a chat packet arrives. */
        void onChatMessage(String packet);
    }

    private int port;
    private ServerSocket welcomeSocket;
    private JoinListener joinListener;
    private ReadyListener readyListener;
    private ChatListener chatListener;

    public Server(int port) {
        this.port = port;
    }

    /** Set your JOIN handler before calling start() */
    public void setJoinListener(JoinListener listener) {
        this.joinListener = listener;
    }
    /** Set your READY handler before calling start() */
    public void setReadyListener(ReadyListener listener) {
        this.readyListener = listener;
    }
    /** Set your chat-message handler before calling start() */
    public void setChatListener(ChatListener listener) {
        this.chatListener = listener;
    }

    /** Begins the accept loop on a background thread. */
    public void start() throws IOException {
        welcomeSocket = new ServerSocket(port);
        new Thread(() -> {
            while (true) {
                try {
                    Socket socket = welcomeSocket.accept();
                    DataInputStream  in  = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    String msg = in.readUTF();

                    if (msg.startsWith("JOIN|")) {
                        // JOIN|host|port
                        String[] parts = msg.split("\\|");
                        String newHost = parts[1];
                        if (joinListener != null) {
                            joinListener.onJoin(newHost, out);
                        }
                        socket.close();
                    }
                    else if (msg.equals("READY")) {
                        if (readyListener != null) {
                            readyListener.onReady();
                        }
                        socket.close();
                    }
                    else {
                        if (chatListener != null) {
                            chatListener.onChatMessage(msg);
                        }
                        socket.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
