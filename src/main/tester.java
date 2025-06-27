package main;

public class tester {
    public static void main(String[] args) {
        Peer a = new Peer("a", 5000, "localhost", 5001);
        a.sendMessage("Hallo");
    }
}
