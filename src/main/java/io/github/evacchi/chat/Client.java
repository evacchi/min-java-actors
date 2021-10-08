package io.github.evacchi.chat;

import io.github.evacchi.Actor;
import io.github.evacchi.chat.ChatServer.Message;

import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.github.evacchi.Actor.*;
import static java.lang.System.*;

public interface Client {

    String host = "localhost";
    int portNumber = 4444;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    Actor.System sys = new Actor.System(Executors.newCachedThreadPool());

    static void main(String[] args) {
        var userName = args[0];
        var socket = new ChatSocket(host, portNumber);

        out.printf("""
                Login........%s
                Local Port...%d
                Server.......%s
                """.stripIndent(), userName, socket.getLocalPort(), socket.getRemoteSocketAddress());

        var serverOut = sys.actorOf(self -> serverOut(self, userName, socket));
        var userIn = sys.actorOf(self -> userInput(self, serverOut));
        var serverSocketReader = sys.actorOf(self -> serverSocketReader(self, socket));

    }

    class Poll {}
    Poll Poll = new Poll();

    static Behavior staying(Consumer<Object> consumer) {
        return msg -> { consumer.accept(msg); return Stay; };
    }

    static Behavior userInput(Address self, Address server) {
        var scanner = new Scanner(in);
        self.tell(Poll);

        return staying(msg -> {
            if (scanner.hasNextLine()) server.tell(new Message(scanner.nextLine()));
            scheduler.schedule(() -> self.tell(Poll), 1, TimeUnit.SECONDS);
        });
    }

    static Behavior serverOut(Address self, String userName, ChatSocket socket) {
        return staying(msg -> {
            if (msg instanceof Message m)
                socket.getOutputWriter().println(userName + " > " + m.text());
        });
    }

    static Behavior serverSocketReader(Address self, ChatSocket socket) {
        self.tell(Poll);

        return staying(msg -> {
            var serverIn = socket.getInputScanner();
            if (serverIn.hasNextLine()) {
                out.println(serverIn.nextLine());
            }
            scheduler.schedule(() -> self.tell(Poll), 1, TimeUnit.SECONDS);
        });
    }
}
