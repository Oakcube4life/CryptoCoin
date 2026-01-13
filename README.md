# CryptoCoin
This is my own **Blockchain** and **Cryptocurrency**.

## How to Run
**For now**, compile all .java files and run main using these commands:
 - javac Block.java Blockchain.java Main.java Message.java Node.java Peer.java Transaction.java
 - java Main

## How it works
When running, the user can start a new node in the network by selecting a port, **multiple nodes can be stored on one computer** by selecting different ports. A local .dat file will be stored in the src/ folder to track a nodes local blockchain.

If a .dat file already exists, it load this file instead. Then it will check with the other nodes if it is behind, and update its blockchain if necessary.

Users can then choose to host or connect to other nodes in the network and mine blocks. When a block in mined it will be broadcasted to all connected nodes in the network. If a node recieves a mined block, it will append it to its blockchain if the new blocks prevHash matches its current tips hash. 

If it does not match correctly, it will broadcast out to the network that it may be behind, it will then update it's blockchain if it is **shorter** than a recieved blockchain. This ensures all nodes have an up-to-date blockchain.

