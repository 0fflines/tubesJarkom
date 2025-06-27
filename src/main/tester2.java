package main;

public class tester2 {
    public static void main(String[] args) {
        Peer a = new Peer("b", 5001, "localhost", 5000);
        a.sendMessage("Hallo");
    }
}