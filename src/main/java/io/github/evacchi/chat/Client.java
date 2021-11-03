package io.github.evacchi.chat;

import io.github.evacchi.Actor;
import io.github.evacchi.chat.ChatBehavior.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.github.evacchi.Actor.Become;
import static io.github.evacchi.Actor.Stay;
import static io.github.evacchi.chat.ChatBehavior.Poll;
import static java.lang.System.in;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public interface Client {

    String host = "localhost";
    int portNumber = 4444;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    Actor.System sys = new Actor.System(Executors.newCachedThreadPool());

    static void main(String[] args) throws IOException {
        var userName = args[0];

        var socket = new Socket(host, portNumber);
        var userInput = new BufferedReader(new InputStreamReader(in));
        var socketInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        var socketOutput = new PrintWriter(socket.getOutputStream(), true);

        out.printf("Login........%s\n", userName);
        out.printf("Local Port...%d\n", socket.getLocalPort());
        out.printf("Server.......%s\n", socket.getRemoteSocketAddress());

        var serverOut = sys.actorOf(self -> ChatBehavior.IOBehavior.of(msg -> {
            if (msg instanceof Message m)
                socketOutput.println(ChatBehavior.Mapper.writeValueAsString(m));
            return Stay;
        }));
        var userIn = sys.actorOf(self -> lineReader(self,
                userInput,
                line -> serverOut.tell(new Message(userName, line))));
        var serverSocketReader = sys.actorOf(self -> lineReader(self,
                socketInput,
                line -> {
                    Message message = ChatBehavior.Mapper.readValue(line, Message.class);
                    out.printf("%s > %s\n", message.user(), message.text());
                }));

    }


    static Actor.Behavior lineReader(Actor.Address self, BufferedReader in, ChatBehavior.IOConsumer<String> lineConsumer) {
        // schedule a message to self
        scheduler.schedule(() -> self.tell(Poll), 100, MILLISECONDS);

        return ChatBehavior.IOBehavior.of(msg -> {
            // ignore non-Poll messages
            if (msg != Poll) return Stay;
            if (in.ready()) {
                var input = in.readLine();
                lineConsumer.accept(input);
            }

            // "stay" in the same state, ensuring that the initializer is re-evaluated
            return Become(lineReader(self, in, lineConsumer));
        });
    }
}
