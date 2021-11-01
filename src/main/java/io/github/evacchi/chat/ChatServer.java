package io.github.evacchi.chat;

import io.github.evacchi.Actor;
import io.github.evacchi.Actor.Address;
import io.github.evacchi.Actor.Behavior;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    static Behavior clientManager(Address self) {
        var clients = new ArrayList<Address>();
        return staying(msg -> {
            switch (msg) {
                // broadcast the message to all connected clients
                case Message m -> clients.forEach(c -> c.tell(m));
                // create a connection handler and a message handler for that client
                case CreateClient n -> {
                    var clientSocketHandler = io.actorOf(me -> clientSocketHandler(me, self, n.socket()));
                    var clientMessageHandler = sys.actorOf(client -> clientMessageHandler(n.socket()));
                    clients.add(clientMessageHandler);
                }
                default -> {}
            }});
    }

    static Behavior serverSocketHandler(Address self, Address clientManager, ChatServerSocket serverSocket) {
        out.printf("Server started at %s.\n", serverSocket.getLocalSocketAddress());

        return poller(() -> scheduler.schedule(() -> self.tell(Poll), 1, SECONDS), msg -> {
            var socket = serverSocket.accept();
            clientManager.tell(new CreateClient(socket));
        });
    }

    static Behavior clientSocketHandler(Address self, Address server, ChatSocket socket) {
        out.println("accepts : " + socket.getRemoteSocketAddress());

        return lineReader(socket.getInputScanner(), () -> scheduler.schedule(() -> self.tell(Poll), 1, SECONDS), input -> {
            out.println(input);
            server.tell(new Message(input));
        });
    }

    static Behavior clientMessageHandler(ChatSocket socket) {
        return staying(msg -> {
            if (msg instanceof Message m) {
                socket.getOutputWriter().println(m.text());
            }
        });
    }



}
