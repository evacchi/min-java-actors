package io.github.evacchi.chat;

import io.github.evacchi.Actor;
import io.github.evacchi.Actor.Address;
import io.github.evacchi.Actor.Behavior;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import static io.github.evacchi.Actor.Stay;
import static io.github.evacchi.chat.ChatBehavior.*;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.SECONDS;

public interface ChatServer {
    int portNumber = 4444;

    Actor.System sys = new Actor.System(Executors.newCachedThreadPool());
    Actor.System io = new Actor.System(Executors.newCachedThreadPool());
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    static void main(String[] args) {
        var clientManager =
                sys.actorOf(self -> clientManager(self));
        var serverSocketHandler =
                io.actorOf(self -> serverSocketHandler(self, clientManager, new ChatServerSocket(portNumber)));
    }

    private static Runnable pollEverySecond(Address self) {
        return () -> scheduler.schedule(() -> self.tell(Poll), 1, SECONDS);
    }

    static Behavior clientManager(Address self) {
        var clients = new ArrayList<Address>();
        return msg -> switch (msg) {
            // broadcast the message to all connected clients
            case Message m -> {
                clients.forEach(c -> c.tell(m));
                yield Stay;
            }
            // create a connection handler and a message handler for that client
            case CreateClient n -> {
                var clientSocketHandler = io.actorOf(me -> clientSocketHandler(me, self, n.socket()));
                var clientMessageHandler = sys.actorOf(client -> clientMessageHandler(n.socket()));
                clients.add(clientMessageHandler);
                yield Stay;
            }
            default -> Stay;
        };
    }

    static Behavior serverSocketHandler(Address self, Address clientManager, ChatServerSocket serverSocket) {
        out.printf("Server started at %s.\n", serverSocket.getLocalSocketAddress());

        return loop(pollEverySecond(self), msg -> {
            var socket = serverSocket.accept();
            clientManager.tell(new CreateClient(socket));
        });
    }

    static Behavior clientSocketHandler(Address self, Address server, ChatSocket socket) {
        out.println("accepts : " + socket.getRemoteSocketAddress());

        return lineReader(pollEverySecond(self), socket.getInputScanner(), input -> {
            out.println(input);
            server.tell(new Message(input));
        });
    }

    static Behavior clientMessageHandler(ChatSocket socket) {
        return msg -> {
            if (msg instanceof Message m) socket.getOutputWriter().println(m.text());
            return Stay;
        };
    }


}
