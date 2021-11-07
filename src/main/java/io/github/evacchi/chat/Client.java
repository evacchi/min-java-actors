package io.github.evacchi.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.evacchi.Actor;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.github.evacchi.Actor.Become;
import static io.github.evacchi.Actor.Stay;
import static java.lang.System.in;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public interface Client {

    interface IOLineReader { void read(String line) throws IOException; }
    interface IOBehavior { Actor.Effect apply(Object msg) throws IOException; }
    static Actor.Behavior IO(IOBehavior behavior) {
        return msg -> {
            try { return behavior.apply(msg); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        };
    }


    String host = "localhost";
    int portNumber = 4444;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    Actor.System sys = new Actor.System(Executors.newCachedThreadPool());

    static Object Poll = new Object();
    record Message(String user, String text) {}

    static void main(String[] args) throws IOException {
        var userName = args[0];

        var mapper = new ObjectMapper();

        var socket = new Socket(host, portNumber);
        var userInput = new BufferedReader(new InputStreamReader(in));
        var socketInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        var socketOutput = new PrintWriter(socket.getOutputStream(), true);

        out.printf("Login........%s\n", userName);
        out.printf("Local Port...%d\n", socket.getLocalPort());
        out.printf("Server.......%s\n", socket.getRemoteSocketAddress());

        var serverOut = sys.actorOf(self -> IO(msg -> {
            if (msg instanceof Message m)
                socketOutput.println(mapper.writeValueAsString(m));
            return Stay;
        }));
        var userIn = sys.actorOf(self -> readLine(self,
                userInput,
                line -> serverOut.tell(new Message(userName, line))));
        var serverSocketReader = sys.actorOf(self -> readLine(self,
                socketInput,
                line -> {
                    Message message = mapper.readValue(line, Message.class);
                    out.printf("%s > %s\n", message.user(), message.text());
                }));
    }

    static Actor.Behavior readLine(Actor.Address self, BufferedReader in, IOLineReader lineReader) {
        // schedule a message to self
        scheduler.schedule(() -> self.tell(Poll), 100, MILLISECONDS);

        return IO(msg -> {
            // ignore non-Poll messages
            if (msg != Poll) return Stay;
            if (in.ready()) {
                var input = in.readLine();
                lineReader.read(input);
            }

            // "stay" in the same state, ensuring that the initializer is re-evaluated
            return Become(readLine(self, in, lineReader));
        });
    }


}
