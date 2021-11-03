package io.github.evacchi.chat;

import io.github.evacchi.Actor;
import io.github.evacchi.Actor.Address;
import io.github.evacchi.Actor.Behavior;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.github.evacchi.Actor.Become;
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
                io.actorOf(self -> serverSocketHandler(self, clientManager))
                        .tell(new ChatServerSocket(portNumber));
    }

//    private static Runnable pollEverySecond(Address self) {
//        return () -> scheduler.schedule(() -> self.tell(Poll), 1, SECONDS);
//    }

    static Behavior clientManager(Address self) {
        var clients = new ArrayList<Address>();
        return msg -> {
            switch (msg) {
                // broadcast the message to all connected clients
                case Message m -> {
                    clients.forEach(c -> c.tell(m));
                    return Stay;
                }
                // create a connection handler and a message handler for that client
                case CreateClient n -> {
                    var clientSocketHandler =
                            io.actorOf(me -> clientSocketHandler(me, self))
                                    .tell(n.socket());
                    var clientMessageHandler =
                            sys.actorOf(client -> clientMessageHandler(n.socket().getOutputWriter()));
                    clients.add(clientMessageHandler);
                    return Stay;
                }
                default -> { return Stay; }
            }
        };
    }

    static Behavior serverSocketHandler(Address self, Address clientManager) {
//        out.printf("Server started at %s.\n", serverSocket.getLocalSocketAddress());



        return msg -> {
            if (msg instanceof ChatServerSocket serverSocket) {
                var socket = serverSocket.accept();
                clientManager.tell(new CreateClient(socket));
                scheduler.schedule(() -> self.tell(serverSocket), 1, SECONDS);
            }
            return Stay;
        };


//        return loop(pollEverySecond(self), msg -> {
//            var socket = serverSocket.accept();
//            clientManager.tell(new CreateClient(socket));
//        });
    }

    static Behavior clientSocketHandler(Address self, Address server) {
//        out.println("accepts : " + socket.getRemoteSocketAddress());

        return msg -> {
            if (msg instanceof ChatSocket socket) {
                var in = socket.getInputScanner();
                if (in.hasNextLine()) {
                    var input = in.nextLine();
                    out.println(input);
                    server.tell(new Message(input));
                }
                scheduler.schedule(() -> self.tell(socket), 1, SECONDS);
            }
            return Stay;
        };

    }

    static Behavior clientMessageHandler(PrintWriter writer) {
        return msg -> {
            if (msg instanceof Message m) writer.println(m.text());
            return Stay;
        };
    }


}
