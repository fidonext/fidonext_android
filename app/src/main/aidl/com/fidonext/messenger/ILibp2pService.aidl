package com.fidonext.messenger;

interface ILibp2pService {
    boolean initializeNode(in String[] bootstrapPeers);
    String getLocalPeerId();
    boolean listen(String address);
    boolean dial(String address);
    boolean sendMessage(in byte[] message);
    byte[] receiveMessage();
    int getAutonatStatus();
    boolean isHealthy();
}
