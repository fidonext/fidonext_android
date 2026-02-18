package com.fidonext.messenger;

interface ILibp2pService {
    boolean initializeNode(in String[] bootstrapPeers);
    String getLocalPeerId();
    String getLocalAccountId();
    String getLocalDeviceId();
    boolean listen(String address);
    boolean dial(String address);
    /**
     * Lookup peer address via DHT directory (account_id or peer_id) and dial it.
     * Returns false when directory record is missing or dialing fails.
     */
    boolean lookupAndDial(String identifier);
    /**
     * Select active chat recipient (peer_id or account_id). Used for encrypted send.
     */
    boolean setActiveRecipient(String identifier);
    boolean sendMessage(in byte[] message);
    byte[] receiveMessage();
    /**
     * Encrypt plaintext for active recipient and publish as chat packet.
     * Returns false if recipient prekey bundle is missing/invalid.
     */
    boolean sendEncryptedMessage(String plaintext);
    /**
     * Returns next decrypted inbound chat message as UTF-8 JSON string, or null.
     * The JSON includes: from_peer_id, to_peer_id, text, kind(prekey|session).
     */
    String receiveDecryptedMessage();
    int getAutonatStatus();
    boolean isHealthy();
    /**
     * Discover peers via DHT (get_closest_peers). Returns peer_id strings, excluding self.
     * May block for several seconds while draining discovery events.
     */
    String[] getDiscoveredPeers();
}
