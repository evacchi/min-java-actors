package io.github.evacchi.chat;

import io.github.evacchi.Actor;
import io.github.evacchi.Actor.*;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.github.evacchi.chat.Client.staying;
import static java.lang.System.*;

public interface ChatServer {
    int portNumber = 4444;

    static void main(String[] args) {
        var sys = new Actor.System(Executors.newCachedThreadPool());
        var clientManager = sys.actorOf(self -> clientManager(sys, self));
        var serverActor = sys.actorOf(self -> serverSocketHandler(sys, self, clientManager, new ChatServerSocket(portNumber)));
        Executors.newScheduledThreadPool(4)
                .scheduleWithFixedDelay(() -> {
                            serverActor.tell(Poll);
                            clientManager.tell(Poll);
                        }, 0, 1, TimeUnit.SECONDS);
    }

    class Poll {}
    Poll Poll = new Poll();
    record Message(String text) {}
    record CreateClient(ChatSocket socket) {}

    static Behavior serverSocketHandler(Actor.System sys, Address self, Address clientManager, ChatServerSocket serverSocket) {
        out.printf("Server started at %s.\n", serverSocket.getLocalSocketAddress());

        return staying(msg -> {
            var socket = serverSocket.accept();
            clientManager.tell(new CreateClient(socket));
        });
    }

    static Behavior clientManager(Actor.System sys, Address self) {
        var clients = new ArrayList<Address>();
        return staying(msg -> {
            switch (msg) {
                case CreateClient n -> clients.add(sys.actorOf(client -> clientOut(sys, self, client, n.socket())));
                case Message m -> clients.forEach(c -> c.tell(m));
                case Poll p -> clients.forEach(c -> c.tell(p));
                default -> {}
            }});
    }

    static Behavior clientOut(Actor.System sys, Address manager, Address self, ChatSocket socket) {
        out.println("accepts : " + socket.getRemoteSocketAddress());
        var clientPoll = sys.actorOf(me -> clientPoll(me, manager, socket));

        return staying(msg -> {
            switch (msg) {
                case Message m -> socket.getOutputWriter().println(m.text());
                case Poll p -> clientPoll.tell(p);
                default -> {}
            }});
    }

    static Behavior clientPoll(Address self, Address server, ChatSocket socket) {
        return staying(msg -> {
            var in = socket.getInputScanner();
            if (in.hasNextLine()) {
                var input = in.nextLine();
                out.println(input);
                server.tell(new Message(input));
            }
        });
    }


}
