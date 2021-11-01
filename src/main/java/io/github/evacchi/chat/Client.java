package io.github.evacchi.chat;

import io.github.evacchi.Actor;
import io.github.evacchi.chat.ChatBehavior.Message;

import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static io.github.evacchi.chat.ChatBehavior.*;
import static java.lang.System.in;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.SECONDS;

public interface Client {

    String host = "localhost";
    int portNumber = 4444;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    Actor.System sys = new Actor.System(Executors.newCachedThreadPool());

    static void main(String[] args) {
        var userName = args[0];
        var socket = new ChatSocket(host, portNumber);

        out.printf("Login........%s\n", userName);
        out.printf("Local Port...%d\n", socket.getLocalPort());
        out.printf("Server.......%s\n", socket.getRemoteSocketAddress());

        var serverOut = sys.actorOf(self -> staying(msg -> {
            if (msg instanceof Message m)
                socket.getOutputWriter().println(userName + " > " + m.text());
        }));
        var userIn = sys.actorOf(self -> lineReader(
                new Scanner(in),
                pollEverySecond(self),
                line -> serverOut.tell(new Message(line))));
        var serverSocketReader = sys.actorOf(self -> lineReader(
                socket.getInputScanner(),
                pollEverySecond(self),
                out::println));
    }

    private static Runnable pollEverySecond(Actor.Address self) {
        return () -> scheduler.schedule(() -> self.tell(Poll), 1, SECONDS);
    }

}
