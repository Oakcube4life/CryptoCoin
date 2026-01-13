/*
 * Gavin MacFadyen
 *
 * This class creates node objects that makeup the network. Nodes can send and recieve messages and blocks 
 * to determine the most up to date blockchain.
*/
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

    //Startup, listens for a connection.
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

    //When a new peer joins, we add them to our peer list so we can broadcast to everyone in the network.
    public void addPeer(String host, int port) {
        for (Peer p : peers) {
            if (p.host.equals(host) && p.port == port) return;
        }
        peers.add(new Peer(host, port));
    }

    //Once connection is found, we can send messages to and from different nodes in the network which have a type and associated data.
    private void handleConnection (Socket socket) {
        try (
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        ) {
            Message msg = (Message) in.readObject();

            switch (msg.type) {
                case "HELLO":
                    int peerPort = (Integer) msg.data;
                    String host = socket.getInetAddress().getHostAddress();

                    addPeer(host, peerPort);
                    //System.out.println("Added peer " + host + ":" + peerPort);

                    out.writeObject(new Message("ACK", null));
                    out.flush();
                    break;
                case "REQUEST_CHAIN":
                    out.writeObject(new Message("SEND_CHAIN", blockchain));
                    out.flush();
                    break;
                case "NEW_BLOCK":
                    Block incoming = (Block) msg.data;

                    //We already have this block in our chain, dont add and send acknowledgment.
                    if (blockchain.containsBlock(incoming.hash)) {
                        out.writeObject(new Message("ACK", null));
                        out.flush();
                        return;
                    }

                    //Add in the recieved block assuming it can be appending directly at the end of this nodes tip (incoming prevHash = currentTips hash, etc)
                    //If we can't add it, we broadcast to the rest of the network letting them know this node is likely behind or on a fork. So we want to compare
                    //Lengths with the other nodes to possibly replace our chain with a more up to date chain.
                    boolean added = blockchain.tryAddBlock(incoming);

                    if (added) {
                        System.out.println("Accepted block " + incoming.index);
                        broadcastBlock(incoming);
                    } else {
                        for (Peer p : peers) {
                            requestChainFromPeer(p.host, p.port);
                        }
                    }

                    out.writeObject(new Message("ACK", null));
                    out.flush();
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void syncWithPeer (String host, int peerPort) {
        //HELLO handshake, this node will introduce itself to the other node so they can add eachother to their peer lists.
        try (
            Socket socket = new Socket(host, peerPort);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            out.writeObject(new Message("HELLO", port));
            out.flush();
            in.readObject(); //ACK
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        //Request the other nodes chain, further explaination below.
        requestChainFromPeer(host, peerPort);
    }

    //For all of the peers in our network we broadcast a given block for them to check and then possibly add to their chain.
    public void broadcastBlock(Block block) {
        for (Peer peer : peers) {
            try (
                Socket socket = new Socket(peer.host, peer.port);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
            ) {
                out.writeObject(new Message("NEW_BLOCK", block));
                out.flush();

                //We don't care what's inside ack just completing handshake.
                in.readObject();
            } catch (Exception e) {
                //System.out.println("Peer offline");
            }
        }
    }

    //REQUEST_CHAIN this broadcasts to a node that it wants its chain, when the other node recieves this message, it will
    //return its own chain so we can compare. Then we may replace our own chain if it is shorter. This is a really simplistic
    //way of finding the most "Up to date" chain, but it works for my project.
    public void requestChainFromPeer(String host, int port) {
        try (
            Socket socket = new Socket(host, port);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            out.writeObject(new Message("REQUEST_CHAIN", null));
            out.flush();

            Message response = (Message) in.readObject();
            Blockchain peerChain = (Blockchain) response.data;

            //This is the length check.
            if (blockchain.maybeReplaceChain(peerChain.getChain())) {
                System.out.println("Chain reorganized");
            }

        } catch (Exception e) {
            e.printStackTrace();
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
