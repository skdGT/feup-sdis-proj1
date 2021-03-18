package jobs;

import files.SavedChunk;
import messages.ChunkMessage;
import messages.Message;
import peer.Peer;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SendChunk implements Runnable {
    private final SavedChunk chunk;
    private final Peer peer;

    public SendChunk(SavedChunk chunk, Peer peer) {
        this.chunk = chunk;
        this.peer = peer;
    }

    @Override
    public void run() {
        if (chunk.isAlreadyProvided()) {
            // System.out.println("[GETCHUNK] I've received a CHUNK message for this chunk so I won't provide it again");
            return;
        }
        if (chunk.getBody() == null) {
            // System.out.println("[GETCHUNK] Something happened and this chunk lost its body!");
            return;
        }

        Message message;
        if (this.peer.getProtocolVersion().equals("1.0")) {
            message = new ChunkMessage(this.peer.getProtocolVersion(), this.peer.getPeerId(), chunk.getFileId(), chunk.getChunkNo(), chunk.getBody());
            this.peer.getMulticastDataRestore().sendMessage(message);
            // no need to keep the body in memory
            int bytes = chunk.getBody().length;
            chunk.clearBody();
            chunk.setBeingHandled(false);
            System.out.printf("[GETCHUNK] Sent %s : %d bytes\n", chunk.getChunkId(), bytes);
        } else {
            try {
                ServerSocket serverSocket = new ServerSocket(0);
                message = new ChunkMessage(this.peer.getProtocolVersion(), this.peer.getPeerId(), chunk.getFileId(), chunk.getChunkNo(),
                        Message.addressPortToBytes(peer.getAddress(), serverSocket.getLocalPort()));
                this.peer.getMulticastDataRestore().sendMessage(message);

                serverSocket.setSoTimeout(2000);
                Socket connection = serverSocket.accept();
                serverSocket.close();

                DataOutputStream stream = new DataOutputStream(connection.getOutputStream());
                stream.write(chunk.getBody(), 0, chunk.getBody().length);
                stream.flush();
                stream.close();
                chunk.clearBody();
                chunk.setBeingHandled(false);
                System.out.printf("[GETCHUNK] [TCP] Sent %s\n", chunk.getChunkId());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
