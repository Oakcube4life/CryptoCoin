import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class Node {
    private final int port;
    private final Blockchain blockchain;
    private final ArrayList<Peer> peers = new ArrayList<>();

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
                new Thread(() -> handleConnection(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleConnection (Socket socket) {
        try (
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        ) {
            Message msg = (Message) in.readObject();

            switch (msg.type) {
                case "REQUEST_CHAIN":
                    out.writeObject(new Message("SEND_CHAIN", blockchain));
                    out.flush();
                    break;
                case "NEW_BLOCK":
                    Block incoming = (Block) msg.data;

                    if (blockchain.containsBlock(incoming.hash)) return; //Ignore block thats already in chain.

                    boolean accepted = blockchain.tryAddBlock(incoming);

                    if (accepted){
                        System.out.println("Block accepted: " + incoming.index);
                        broadcastBlock(incoming);
                    }
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addPeer (String host, int port) {
        Peer newPeer = new Peer(host, port);
        peers.add(newPeer);
    }

    public void syncWithPeer (String host, int peerPort) {
        try (
                Socket socket = new Socket(host, peerPort);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        ) {
            out.writeObject(new Message("REQUEST_CHAIN", null));
            out.flush();

            Message response = (Message) in.readObject();
            Blockchain peerChain = (Blockchain) response.data;

            if (peerChain.length() > blockchain.length()) {
                blockchain.replaceChain(peerChain);
                System.out.println("Replaced chain.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void broadcastBlock (Block block) {
        for (Peer peer : peers) {
            try (
                Socket socket = new Socket(peer.host, peer.port);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ) {
                out.writeObject(new Message("NEW_BLOCK", block));
                out.flush();
            } catch (Exception e) {
                System.out.println("Peer offline.");
            }
        }
    }

    public void mineAndBroadcast (Block block) {
        blockchain.addBlock(block);
        broadcastBlock(block);
    }

    public Blockchain getBlockchain () {
        return blockchain;
    }
}
