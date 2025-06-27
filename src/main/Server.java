package main;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Server {
    private final int port;
    private Socket clientSocket;
    private ServerSocket welcomeSocket;

    public Server(int port){
        this.port = port;
    }

    public Scanner initConnection(){
        try{
            welcomeSocket = new ServerSocket(port);
            System.out.println("port "+port+" open");
            clientSocket = welcomeSocket.accept();
            System.out.println("port "+ port +" connected");
            return new Scanner(clientSocket.getInputStream());
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
