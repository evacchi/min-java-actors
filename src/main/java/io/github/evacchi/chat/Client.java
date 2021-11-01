package io.github.evacchi.chat;

import io.github.evacchi.Actor;
import io.github.evacchi.chat.ChatServer.Message;

import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.github.evacchi.Actor.Address;
import static io.github.evacchi.Actor.Behavior;
import static io.github.evacchi.chat.ChatBehavior.lineReader;
import static io.github.evacchi.chat.ChatBehavior.staying;
import static java.lang.System.in;
import static java.lang.System.out;

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

    static Behavior userInput(Address self, Address server) {
        return lineReader(self, new Scanner(in), scheduler, line -> server.tell(new Message(line)));
    }

    static Behavior serverOut(Address self, String userName, ChatSocket socket) {
        return staying(msg -> {
            if (msg instanceof Message m)
                socket.getOutputWriter().println(userName + " > " + m.text());
        });
    }

    static Behavior serverSocketReader(Address self, ChatSocket socket) {
        return lineReader(self, socket.getInputScanner(), scheduler, out::println);
    }
}
