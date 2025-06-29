package main;

import java.util.Scanner;

/**
 * Kelas sederhana untuk memulai sebuah instance dari Peer.
 * Kelas ini berfungsi sebagai titik masuk (entry point) untuk aplikasi
 * P2P berbasis konsol.
 */
public class tester {

    public static void main(String[] args) {
        // Gunakan Scanner untuk membaca input dari konsol.
        Scanner scanner = new Scanner(System.in);

        System.out.println("--- P2P Chat Application ---");
        System.out.print("Masukkan username Anda untuk bergabung ke jaringan: ");
        
        // Baca username dari pengguna.
        String username = scanner.nextLine().trim();

        // Validasi sederhana agar username tidak kosong.
        if (username.isEmpty()) {
            System.out.println("Username tidak boleh kosong. Aplikasi akan keluar.");
            scanner.close();
            return;
        }

        System.out.println("\nSelamat datang, " + username + "!");
        System.out.println("Memulai peer dan mencoba bergabung ke jaringan...");

        // Membuat objek Peer baru.
        // Konstruktor Peer akan secara otomatis memulai semua proses yang diperlukan
        // (menjalankan server, melakukan discovery, dan memulai input loop).
        new Peer(username);

        // Catatan: Program tidak akan langsung selesai di sini.
        // Thread utama akan "tertahan" di dalam metode startChatIO() di kelas Peer,
        // yang berisi loop Scanner untuk membaca perintah dari Anda.
    }
}
