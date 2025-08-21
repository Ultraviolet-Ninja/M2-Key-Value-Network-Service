package jasmine.jragon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public final class Client {
    public static final String DEFAULT_HOST = "127.0.0.1";

    private static final Logger LOG = LoggerFactory.getLogger(Client.class);

    public static void main(String[] args) {
        var userIn = new Scanner(System.in);

        try (var serverSocket = SocketChannel.open()) {
            var buffer = ByteBuffer.allocate(1024);
            var connectionDetails = args.length > 1 ? args[1].split(":") : new String[0];
            var ipAddress = connectionDetails.length == 2 ? connectionDetails[0] : DEFAULT_HOST;
            int port = Server.PORT;
            try {
                if (connectionDetails.length == 2) {
                    port = Integer.parseInt(connectionDetails[1]);
                }
            } catch (NumberFormatException e) {
                LOG.warn("Invalid port number: {}", args[1]);
            }
            serverSocket.connect(new InetSocketAddress(ipAddress, port));
            serverSocket.configureBlocking(true);

            while (true) {
                System.out.println("Enter Command:");
                var command = userIn.nextLine();

                if (command.equalsIgnoreCase("exit")) break;

                buffer.clear().put(command.getBytes(StandardCharsets.UTF_8)).flip();
                while (buffer.hasRemaining()) {
                    serverSocket.write(buffer);
                }

                buffer.clear();

                int n = serverSocket.read(buffer);
                buffer.flip();

                String response = new String(buffer.array(), buffer.position(), n);
                System.out.println("Server said: " + response);
            }
        } catch (IOException e) {
            LOG.error("Premature Client Shutdown: ", e);
        }
    }
}
