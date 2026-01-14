/*
 * Gavin MacFadyen
 *
 * This class creates node objects that makeup the network. Nodes can send transactions given another nodes public key.
 * Blocks can be mined and broadcasted to determine the most up to date blockchain.
*/
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public class Node {
    private final int port;
    private final Blockchain blockchain;
    private final ArrayList<Peer> peers = new ArrayList<>();

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    private final Set<Transaction> mempool = ConcurrentHashMap.newKeySet();
    private final Set<String> seenTransactions = ConcurrentHashMap.newKeySet();

    public Node (int port) throws Exception {
        this.port = port;

        //Create keys for transaction validations
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();

        String filename = "blockchain_" + port + ".dat";
        this.blockchain = Blockchain.loadFromDisk(filename);
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

    //We need to broadcast to other nodes when we are disconnecting so they can remove this node from their peer list.
    public void disconnect() {
        for (Peer peer : peers) {
            try (
                Socket socket = new Socket(peer.host, peer.port);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            ) {
                out.writeObject(new Message("DISCONNECT", port));
                out.flush();
                in.readObject();
            } catch (Exception ignored) {}
        }
    }


    //When a new peer joins, we add them to our peer list so we can broadcast to everyone in the network.
    public void addPeer (String host, int port) {
        for (Peer peer : peers) {
            if (peer.host.equals(host) && peer.port == port) return;
        }
        peers.add(new Peer(host, port));
    }

    //These methods are used to create and verify transactions locally.
    public Transaction createTransaction (String reciever, int amount) throws Exception {
        String senderKey = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        Transaction tx = new Transaction(senderKey, reciever, amount);
        tx.sign(privateKey);
        return tx;
    }

    public void addTransactionToMempool (Transaction tx) throws Exception {
        if (tx.verify() && !seenTransactions.contains(tx.txId)) {
            seenTransactions.add(tx.txId);
            mempool.add(tx);
        }
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

                    //Confirm that all of the transactions in the incoming block are verified.
                    for (Transaction tx : incoming.transactions) {
                        if (!tx.verify()) {
                            System.out.println("Rejected block due to invalid transaction");
                            out.writeObject(new Message("ACK", null));
                            out.flush();
                            return;
                        }
                    }

                    //Add in the recieved block assuming it can be appending directly at the end of this nodes tip (incoming prevHash = currentTips hash, etc)
                    //If we can't add it, we broadcast to the rest of the network letting them know this node is likely behind or on a fork. So we want to compare
                    //Lengths with the other nodes to possibly replace our chain with a more up to date chain.
                    boolean added = blockchain.tryAddBlock(incoming);

                    if (added) {
                        System.out.println("Accepted block: " + incoming.index);
                        mempool.removeAll(incoming.transactions);
                        broadcastBlock(incoming);
                    } else {
                        for (Peer p : peers) {
                            requestChainFromPeer(p.host, p.port);
                        }
                    }

                    out.writeObject(new Message("ACK", null));
                    out.flush();
                    break;
                case "NEW_TX":
                    Transaction tx = (Transaction) msg.data;

                    if (!tx.verify()) {
                        System.out.println("Rejected transaction (invalid signature)");
                        break;
                    }

                    if (seenTransactions.contains(tx.txId)) {
                        //Duplicate transaction, not necessary to print, only good for debugging.
                        //System.out.println("Duplicate transaction ignored " + tx.txId);
                        break;
                    }

                    //First time seeing this TX, add it to them mempool
                    seenTransactions.add(tx.txId);
                    mempool.add(tx);
                    broadcastTransaction(tx);
                    System.out.println("Accepted transaction: " + tx.txId);


                    out.writeObject(new Message("ACK", null));
                    out.flush();
                    break;
                case "DISCONNECT":
                    int disconnectPeerPort = (Integer) msg.data;
                    String disconnectHost = socket.getInetAddress().getHostAddress();

                    peers.removeIf(p -> p.host.equals(disconnectHost) && p.port == disconnectPeerPort);
                    System.out.println("Peer disconnected " + disconnectHost + ":" + disconnectPeerPort);
                    break;
            }
        } catch (SocketException e) {
            // Peer disconnected abruptly normal
        } catch (EOFException e) {
            // Peer closed connection cleanly normal
        } catch (Exception e) {
            // Actual unexpected error
            e.printStackTrace();
        }
    }

    public void syncWithPeer (String host, int peerPort) {
        addPeer(host, peerPort);

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

    //REQUEST_CHAIN this broadcasts to a node that it wants its chain, when the other node recieves this message, it will
    //return its own chain so we can compare. Then we may replace our own chain if it is shorter. This is a really simplistic
    //way of finding the most "Up to date" chain, but it works for my project.
    public void requestChainFromPeer (String host, int port) {
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
                blockchain.saveToDisk("blockchain_" + port + ".dat");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void mineFromMempool () {
        if (mempool.isEmpty()) return;

        //Copy transactions from mempool
        ArrayList<Transaction> txs = new ArrayList<>(mempool);

        //Create new block shell
        Block prev = blockchain.getLatestBlock();
        Block block = new Block(prev.index + 1, prev.hash);
        block.transactions = txs;

        //Reset the hash because we changed the transactions list
        block.hash = block.computeHash();

        //Mine and add it to the chain
        blockchain.addBlock(block);
        blockchain.saveToDisk("blockchain_" + port + ".dat");

        //Remove mined transactions
        mempool.removeAll(txs);

        //Broadcast the block
        broadcastBlock(block);
        System.out.println("Mined and broadcasted block " + block.index);
    }

    //For all of the peers in our network we broadcast a given block for them to check and then possibly add to their chain.
    public void broadcastBlock (Block block) {
        for (Peer peer : new ArrayList<>(peers)) {
            try (
                Socket socket = new Socket(peer.host, peer.port);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
            ) {
                out.writeObject(new Message("NEW_BLOCK", block));
                out.flush();
                in.readObject(); //ACK
            } catch (ConnectException | SocketTimeoutException e) {
                removePeer(peer); //Peer truly unreachable
            } catch (EOFException | SocketException e) {
                //Peer closed early normal
            } catch (Exception e) {
                //Unexpected error
                e.printStackTrace();
            }
        }
    }

    //Similar to broadcasting blocks. Each time we make a new transaction we have to broadcast it to all of our peers.
    public void broadcastTransaction (Transaction tx) {
        for (Peer peer : new ArrayList<>(peers)) {
            try (
                Socket socket = new Socket(peer.host, peer.port);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
            ) {
                out.writeObject(new Message("NEW_TX", tx));
                out.flush();
                in.readObject(); // ACK
            } catch (ConnectException | SocketTimeoutException e) {
                removePeer(peer); //Peer truly unreachable
            } catch (EOFException | SocketException e) {
                //Peer closed early normal
            } catch (Exception e) {
                //Unexpected error
                e.printStackTrace();
            }
        }
    }

    //When a peer disconnects, we must remove them from our peer list
    private synchronized void removePeer (Peer peer) {
        peers.remove(peer);
        //System.out.println("Removed peer " + peer.host + ":" + peer.port); Annoying, save for debugging.
    }

    //These methods below are mostly print statements for the CLI.
    public String getPublicKeyBase64 () {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public void printMempool () {
        if (mempool.isEmpty()) {
            System.out.println("(empty)");
            return;
        }

        for (Transaction tx : mempool) {
            System.out.println(tx.txId + " | " + tx.amount);
        }
    }

    public void printPeers () {
        if (peers.isEmpty()) {
            System.out.println("(no peers)");
            return;
        }

        for (Peer p : peers) {
            System.out.println(p.host + ":" + p.port);
        }
    }

    public Blockchain getBlockchain () {
        return blockchain;
    }
}