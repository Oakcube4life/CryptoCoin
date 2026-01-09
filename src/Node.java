import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Node {
    private final int port;
    private final Blockchain blockchain;

    public Node (int port) {
        this.port = port;
        this.blockchain = new Blockchain();
    }

    public void start () {
        new Thread(this::listen).start();
        System.out.println("Listening on port " + port + "...");
    }

    private void listen () {
        try (ServerSocket server = new ServerSocket(port)) {
            while (true) {
                Socket socket = server.accept();
                handleConnection(socket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleConnection (Socket socket) {
        try (
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        ) {
            Message msg = (Message) in.readObject();

            if (msg.type.equals("REQUEST_CHAIN")) {
                out.writeObject(new Message("SEND_CHAIN", blockchain));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void syncWithPeer (String host, int peerPort) {
        try (
            Socket socket = new Socket(host, peerPort);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        ) {
            out.writeObject(new Message("REQUEST_CHAIN", null));
            Message response = (Message) in.readObject();

            if (response.type.equals("SEND_CHAIN")) {
                Blockchain peerChain = (Blockchain) response.data;

                if (peerChain.length() > blockchain.length()) {
                    blockchain.replaceChain(peerChain);
                    System.out.println("Replaced Chain!");
                } else {
                    System.out.println("Chain is up to date!");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Blockchain getBlockchain () {
        return blockchain;
    }
}
