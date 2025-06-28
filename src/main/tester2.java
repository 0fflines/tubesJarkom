package main;

import java.util.Scanner;

public class tester2 {
    public static void main(String[] args) {
        Peer a = new Peer("b", 5001, "localhost", 5000);
        Scanner sc = new Scanner(System.in);
        a.sendMessage(sc.nextLine());
    }
}