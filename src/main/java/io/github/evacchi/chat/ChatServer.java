package io.github.evacchi.chat;

import io.github.evacchi.Actor;
import io.github.evacchi.Actor.Address;
import io.github.evacchi.Actor.Behavior;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.github.evacchi.Actor.Become;
import static io.github.evacchi.Actor.Stay;
import static io.github.evacchi.chat.ChatBehavior.*;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public interface ChatServer {
    int portNumber = 4444;

    Actor.System sys = new Actor.System(Executors.newCachedThreadPool());
    Actor.System io = new Actor.System(Executors.newCachedThreadPool());
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    static void main(String... args) throws IOException {
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
                // create a connection handler and a message handler for that client
                case CreateClient n -> {
                    var socket = n.socket();
                    out.println("accepts : " + socket.getRemoteSocketAddress());

                    var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    var clientSocketHandler =
                            io.actorOf(me -> clientInput(me, self, in));

                    var writer = new PrintWriter(socket.getOutputStream(), true);
                    var clientMessageHandler =
                            sys.actorOf(client -> clientOutput(writer));

                    clients.add(clientMessageHandler);
                    return Stay;
                }
                // broadcast the message to all connected clients
                case Message m -> {
                    clients.forEach(c -> c.tell(m));
                    return Stay;
                }
                // ignore all other messages
                default -> {
                    return Stay;
                }
            }
        });
    }

    static Behavior serverSocketHandler(Address self, Address clientManager, ServerSocket serverSocket) {
        scheduler.schedule(() -> self.tell(Poll), 1000, MILLISECONDS);
        return IOBehavior.of(msg -> {
            if (msg != Poll) return Stay;
            var socket = serverSocket.accept();
            clientManager.tell(new CreateClient(socket));
            return Become(serverSocketHandler(self, clientManager, serverSocket));
        });
    }

    static Behavior clientInput(Address self, Address clientManager, BufferedReader in) {
        // schedule a message to self
        scheduler.schedule(() -> self.tell(Poll), 100, MILLISECONDS);

        return IOBehavior.of(msg -> {
            // ignore non-Poll messages
            if (msg != Poll) return Stay;
            if (in.ready()) {
                var input = in.readLine();
                // log message to stdout
                out.println(input);
                // deserialize from JSON
                Message m = Mapper.readValue(input, Message.class);
                // broadcast to all other clients
                clientManager.tell(m);
            }

            // "stay" in the same state, ensuring that the initializer is re-evaluated
            return Become(clientInput(self, clientManager, in));
        });
    }

    static Behavior clientOutput(PrintWriter writer) {
        return IOBehavior.of(msg -> {
            if (msg instanceof Message m) writer.println(Mapper.writeValueAsString(m));
            return Stay;
        });
    }


}
