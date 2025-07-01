package Jarkom.chatapp.network;

import java.util.List;

public interface P2P_Callbacks {
    void onMessageReceived(String roomName, String sender, String message);
    void onRoomListUpdated(List<String> roomNames);
    void onSystemMessage(String message);
    void onConnectionStatusChanged(boolean isConnected);
}
