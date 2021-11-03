package io.github.evacchi.chat;

import io.github.evacchi.Actor;
import io.github.evacchi.chat.ChatBehavior.Message;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import static io.github.evacchi.Actor.Become;
import static io.github.evacchi.Actor.Stay;
import static io.github.evacchi.chat.ChatBehavior.Poll;
import static java.lang.System.in;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.SECONDS;

public interface Client {

    String host = "localhost";
    int portNumber = 4444;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    Actor.System sys = new Actor.System(Executors.newCachedThreadPool());

    static void main(String[] args) throws IOException {
        var userName = args[0];

            var socket = new Socket(host, portNumber);
            var userInput = new Scanner(in);
            var socketInput = new Scanner(socket.getInputStream());
            var socketOutput = new PrintWriter(socket.getOutputStream(), true);

            out.printf("Login........%s\n", userName);
            out.printf("Local Port...%d\n", socket.getLocalPort());
            out.printf("Server.......%s\n", socket.getRemoteSocketAddress());

            var serverOut = sys.actorOf(self -> msg -> {
                if (msg instanceof Message m)
                    socketOutput.println(userName + " > " + m.text());
                return Stay;
            });
            var userIn = sys.actorOf(self -> lineReader(self,
                    userInput,
                    line -> serverOut.tell(new Message(line))));
            var serverSocketReader = sys.actorOf(self -> lineReader(self,
                    socketInput,
                    out::println));

    }

    static Actor.Behavior lineReader(Actor.Address self, Scanner in, Consumer<String> lineConsumer) {
        scheduler.schedule(() -> self.tell(Poll), 1, SECONDS);
        return msg -> {
            if (msg != Poll) return Stay;

            if (in.hasNextLine()) {
                var input = in.nextLine();
                lineConsumer.accept(input);
            }
            return Become(lineReader(self, in, lineConsumer));
        };
    }

}
