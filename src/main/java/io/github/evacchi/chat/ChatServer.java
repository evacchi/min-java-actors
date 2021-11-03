package io.github.evacchi.chat;

import io.github.evacchi.Actor;
import io.github.evacchi.Actor.Address;
import io.github.evacchi.Actor.Behavior;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.github.evacchi.Actor.Become;
import static io.github.evacchi.Actor.Stay;
import static io.github.evacchi.chat.ChatBehavior.*;
import static java.lang.System.err;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.SECONDS;

public interface ChatServer {
    int portNumber = 4444;

    Actor.System sys = new Actor.System(Executors.newCachedThreadPool());
    Actor.System io = new Actor.System(Executors.newCachedThreadPool());
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    static void main(String[] args) throws IOException {
        var serverSocket = new ServerSocket(portNumber);
        out.printf("Server started at %s.\n", serverSocket.getLocalSocketAddress());

        var clientManager =
                sys.actorOf(self -> clientManager(self));
        var serverSocketHandler =
                io.actorOf(self -> serverSocketHandler(self, clientManager, serverSocket));
    }

    static Behavior clientManager(Address self) {
        var clients = new ArrayList<Address>();
        return IOBehavior.of(msg -> {
            switch (msg) {
                // broadcast the message to all connected clients
                case Message m -> {
                    clients.forEach(c -> c.tell(m));
                    return Stay;
                }
                // create a connection handler and a message handler for that client
                case CreateClient n -> {
                    var socket = n.socket();
                    out.println("accepts : " + socket.getRemoteSocketAddress());

                    var in = new Scanner(socket.getInputStream());
                    var clientSocketHandler =
                            io.actorOf(me -> clientSocketHandler(me, self, in));

                    var writer = new PrintWriter(socket.getOutputStream(), true);
                    var clientMessageHandler =
                            sys.actorOf(client -> clientMessageHandler(writer));

                    clients.add(clientMessageHandler);
                    return Stay;
                }
                default -> {
                    return Stay;
                }
            }
        });
    }

    static Behavior serverSocketHandler(Address self, Address clientManager, ServerSocket serverSocket) {
        scheduler.schedule(() -> self.tell(Poll), 1, SECONDS);
        return IOBehavior.of(msg -> {
            if (msg != Poll) return Stay;
            var socket = serverSocket.accept();
            clientManager.tell(new CreateClient(socket));
            return Become(serverSocketHandler(self, clientManager, serverSocket));
        });
    }

    static Behavior clientSocketHandler(Address self, Address server, Scanner in) {
        scheduler.schedule(() -> self.tell(Poll), 1, SECONDS);

        return msg -> {
            if (msg != Poll) return Stay;
            if (in.hasNextLine()) {
                var input = in.nextLine();
                out.println(input);
                server.tell(new Message(input));
            }
            return Become(clientSocketHandler(self, server, in));
        };

    }

    static Behavior clientMessageHandler(PrintWriter writer) {
        return msg -> {
            if (msg instanceof Message m) writer.println(m.text());
            return Stay;
        };
    }


}
