package io.github.evacchi.chat;

import io.github.evacchi.Actor;
import io.github.evacchi.chat.ChatServer.Message;

import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.github.evacchi.Actor.*;
import static java.lang.System.*;

public interface Client {

    String host = "localhost";
    int portNumber = 4444;

    static void main(String[] args) {
        var userName = args[0];
        var socket = new ChatSocket(host, portNumber);

        out.printf("""
                Login........%s
                Local Port...%d
                Server.......%s
                """.stripIndent(), userName, socket.getLocalPort(), socket.getRemoteSocketAddress());

        var sys = new Actor.System(Executors.newCachedThreadPool());
        var serverOut = sys.actorOf(self -> serverOut(self, userName, socket));
        var userIn = sys.actorOf(self -> userInput(self, serverOut));
        var poller = sys.actorOf(self -> serverPoll(self, socket));

        Executors.newScheduledThreadPool(4)
                .scheduleWithFixedDelay(() -> {
                            userIn.tell(Poll);
                            poller.tell(Poll);
                        }, 0, 1, TimeUnit.SECONDS);

    }

    class Poll {}
    Poll Poll = new Poll();

    static Behavior staying(Consumer<Object> consumer) {
        return msg -> { consumer.accept(msg); return Stay; };
    }

    static Behavior userInput(Address self, Address server) {
        var scanner = new Scanner(in);

        return staying(msg -> {
            if (scanner.hasNextLine()) server.tell(new Message(scanner.nextLine()));
        });
    }

    static Behavior serverOut(Address self, String userName, ChatSocket socket) {
        return staying(msg -> {
            if (msg instanceof Message m)
                socket.getOutputWriter().println(userName + " > " + m.text());
        });
    }

    static Behavior serverPoll(Address self, ChatSocket socket) {
        return staying(msg -> {
            var serverIn = socket.getInputScanner();
            if (serverIn.hasNextLine()) {
                out.println(serverIn.nextLine());
            }
        });
    }
}
