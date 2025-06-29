package main;

import java.util.Scanner;

public class tester {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String username = sc.next();
        Peer peer = new Peer(username);
    }
}
