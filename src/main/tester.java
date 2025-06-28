package main;

import java.util.Scanner;

public class tester {
    public static void main(String[] args) {
        Peer a = new Peer("a", 5000, "localhost", 5001);
        Scanner sc = new Scanner(System.in);
        a.sendMessage(sc.nextLine());
    }
}
