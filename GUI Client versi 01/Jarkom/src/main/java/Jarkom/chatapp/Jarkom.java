/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package Jarkom.chatapp;

import Jarkom.chatapp.forms.LoginForm;

/**
 *
 * @author azrie
 */
public class Jarkom {
    public static void main(String[] args) {
        /* Create and display the login form */
        java.awt.EventQueue.invokeLater(() -> {
            new LoginForm().setVisible(true);
        });
    }
}
