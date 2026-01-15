/*
 * Gavin MacFadyen
 *
 * This class creates node objects that makeup the network. A network participant that creates transactions,
 * mines blocks, maintains a mempool, and stays synchronized with peers. All consensus rules are enforced by the Blockchain.
*/
import java.util.*;
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

public class Node {
    private final int port;
    private final Blockchain blockchain;
    private final ArrayList<Peer> peers = new ArrayList<>();

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    private final Set<Transaction> mempool = ConcurrentHashMap.newKeySet();
    private final Set<String> seenTransactions = ConcurrentHashMap.newKeySet();

    private final Set<String> mempoolSpentUTXOs = ConcurrentHashMap.newKeySet();

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
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
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

    // Builds and signs a transaction using this node's available UTXOs, but does not commit it.
    public Transaction createTransaction(PublicKey recipient, long amount) throws Exception {
        long total = 0;
        List<TransactionInput> inputs = new ArrayList<>();

        //Collect UTXOs owned by this node
        for (Map.Entry<String, TransactionOutput> entry : blockchain.getUTXO().entrySet()) {
            TransactionOutput out = entry.getValue();

            if (!out.recipient.equals(publicKey)) continue;

            // Skip UTXOs already locked in mempool
            if (mempoolSpentUTXOs.contains(out.id)) continue;

            inputs.add(new TransactionInput(out.id));
            total += out.amount;

            if (total >= amount) break;
        }

        if (total < amount) {
            throw new Exception("Insufficient funds");
        }

        //Create outputs
        List<TransactionOutput> outputs = new ArrayList<>();

        //Payment output
        outputs.add(new TransactionOutput(recipient, amount));

        //Change output (if any)
        long change = total - amount;
        if (change > 0) {
            outputs.add(new TransactionOutput(publicKey, change));
        }

        //Create and sign transaction
        Transaction tx = new Transaction(publicKey, inputs, outputs);
        tx.sign(privateKey);

        return tx;
    }


    public synchronized void addTransactionToMempool(Transaction tx) throws Exception {

        if (!blockchain.validateTransaction(tx)) {
            throw new Exception("Invalid transaction");
        }

        for (TransactionInput in : tx.inputs) {
            if (mempoolSpentUTXOs.contains(in.outputId)) {
                throw new Exception("Double-spend in mempool");
            }
        }

        mempool.add(tx);

        for (TransactionInput in : tx.inputs) {
            mempoolSpentUTXOs.add(in.outputId);
        }
    }

    //Once connection is found, we can send messages to and from different nodes in the network which have a type and associated data.
    private void handleConnection (Socket socket) {
        try (
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            Message msg = (Message) in.readObject();

            switch (msg.type) {
                case "HELLO":
                    int peerPort = (Integer) msg.data;
                    String host = socket.getInetAddress().getHostAddress();

                    addPeer(host, peerPort);
                    System.out.println("Added peer " + host + ":" + peerPort);

                    out.writeObject(new Message("ACK", null));
                    out.flush();
                    break;
                case "REQUEST_CHAIN":
                    out.writeObject(new Message("SEND_CHAIN", blockchain));
                    out.flush();
                    break;
                case "NEW_BLOCK":
                    Block incoming = (Block) msg.data;

                    //Ignore blocks we already have
                    if (blockchain.containsBlock(incoming.hash)) {
                        out.writeObject(new Message("ACK", null));
                        out.flush();
                        break;
                    }

                    boolean added;
                    try {
                        //Full validation + UTXO application happens here
                        added = blockchain.tryAddBlock(incoming);
                    } catch (Exception e) {
                        //Invalid block (bad tx, bad UTXO, etc.)
                        out.writeObject(new Message("ACK", null));
                        out.flush();
                        break;
                    }

                    if (added) {
                        System.out.println("Accepted block: " + incoming.index);

                        //Remove confirmed txs from mempool
                        mempool.removeAll(incoming.transactions);

                        //Release mempool UTXO locks
                        for (Transaction tx : incoming.transactions) {
                            for (TransactionInput txIn : tx.inputs) {
                                mempoolSpentUTXOs.remove(txIn.outputId);
                            }
                        }

                        //Gossip block further
                        broadcastBlock(incoming);

                    } else {
                        //Likely fork or we're behind → request chains
                        for (Peer p : peers) {
                            requestChainFromPeer(p.host, p.port);
                        }
                    }

                    out.writeObject(new Message("ACK", null));
                    out.flush();
                    break;
                case "NEW_TX":
                    Transaction tx = (Transaction) msg.data;

                    try {
                        //Ignore duplicates early
                        if (seenTransactions.contains(tx.txId)) {
                            break;
                        }

                        //Full validation and mempool locking
                        addTransactionToMempool(tx);

                        //Mark as seen *after* successful acceptance
                        seenTransactions.add(tx.txId);

                        //Gossip further
                        broadcastTransaction(tx);

                        System.out.println("Accepted transaction: " + tx.txId);

                    } catch (Exception e) {
                        //Invalid tx ignore silently bc annoying
                    }

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
            //Peer disconnected abruptly normal
        } catch (EOFException e) {
            //Peer closed connection cleanly normal
        } catch (Exception e) {
            //Actual unexpected error
            e.printStackTrace();
        }
    }

    public void syncWithPeer (String host, int peerPort) {
        addPeer(host, peerPort);

        //HELLO handshake, this node will introduce itself to the other node so they can add each other to their peer lists.
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

        //Request the other nodes chain, further explanation below.
        requestChainFromPeer(host, peerPort);
    }

    //REQUEST_CHAIN this broadcasts to a node that it wants its chain, when the other node receives this message, it will
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

            if (blockchain.maybeReplaceChain(peerChain.getChain())) {
                System.out.println("Chain reorganized");

                //Reset local transaction state.
                mempool.clear();
                mempoolSpentUTXOs.clear();
                seenTransactions.clear();

                //Persist new canonical chain.
                blockchain.saveToDisk("blockchain_" + this.port + ".dat");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Mines a new block using the current mempool contents. A coinbase transaction is always created to reward this node for mining,
    //and any pending transactions in the mempool are included if present. The block is mined locally by performing proof-of-work, then validated
    //and added to the blockchain. If accepted, the mempool and related locks are cleared and the new block is broadcast to peers.
    public void mineFromMempool() throws Exception {
        ArrayList<Transaction> txs = new ArrayList<>();

        // Add coinbase tx always
        TransactionOutput reward = new TransactionOutput(publicKey, 1);
        Transaction coinbase = new Transaction(publicKey, List.of(), List.of(reward));
        coinbase.signature = new byte[0];
        txs.add(coinbase);

        // Add mempool txs (if any)
        txs.addAll(mempool);

        Block prev = blockchain.getLatestBlock();
        Block block = new Block(prev.index + 1, prev.hash);
        block.transactions = txs;
        block.hash = block.computeHash();

        while (!block.hash.startsWith("0".repeat(5))) {
            block.nonce++;
            block.hash = block.computeHash();
        }

        if (!blockchain.tryAddBlock(block)) return;

        blockchain.saveToDisk("blockchain_" + port + ".dat");

        mempool.clear();
        mempoolSpentUTXOs.clear();
        seenTransactions.clear();

        broadcastBlock(block);
        System.out.println("Mined block " + block.index);
    }

    //For all the peers in our network we broadcast a given block for them to check and then possibly add to their chain.
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

    //These methods below are "Getters" and print statements for the CLI.
    public String getPublicKeyBase64 () {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public Blockchain getBlockchain () {
        return blockchain;
    }

    public long getBalance() {
        return blockchain.getBalance(publicKey);
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

    public void printMempool() {
        if (mempool.isEmpty()) {
            System.out.println("(mempool empty)");
            return;
        }
        for (Transaction tx : mempool) {
            System.out.println(tx.txId);
        }
    }

    public void printUTXO() {
        blockchain.printUTXO(); // expose from Blockchain
    }
}