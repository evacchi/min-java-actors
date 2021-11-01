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

public interface ChatServer {
    int portNumber = 4444;

    Actor.System sys = new Actor.System(Executors.newCachedThreadPool());
    Actor.System io = new Actor.System(Executors.newCachedThreadPool());
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    static void main(String[] args) {
        var clientManager = sys.actorOf(self -> clientManager(self));
        var serverActor = io.actorOf(self -> serverSocketHandler(self, clientManager, new ChatServerSocket(portNumber)));
    }

    record Message(String text) {}
    record CreateClient(ChatSocket socket) {}

    static Behavior serverSocketHandler(Address self, Address clientManager, ChatServerSocket serverSocket) {
        out.printf("Server started at %s.\n", serverSocket.getLocalSocketAddress());

        return poller(self, scheduler, msg -> {
            var socket = serverSocket.accept();
            clientManager.tell(new CreateClient(socket));
        });
    }

    static Behavior clientManager(Address self) {
        var clients = new ArrayList<Address>();
        return staying(msg -> {
            switch (msg) {
                case CreateClient n -> {
                    var clientSocketHandler = io.actorOf(me -> clientSocketHandler(me, self,  n.socket()));
                    var clientMessageHandler = sys.actorOf(client -> clientOut(n.socket()));
                    clients.add(clientMessageHandler);
                }
                case Message m -> clients.forEach(c -> c.tell(m));
                default -> {}
            }});
    }

    static Behavior clientOut(ChatSocket socket) {
        return staying(msg -> {
            if (msg instanceof Message m) {
                socket.getOutputWriter().println(m.text());
            }
        });
    }

    static Behavior clientSocketHandler(Address self, Address server, ChatSocket socket) {
        out.println("accepts : " + socket.getRemoteSocketAddress());

        return lineReader(self, socket.getInputScanner(), scheduler, input -> {
            out.println(input);
            server.tell(new Message(input));
        });
    }


}
