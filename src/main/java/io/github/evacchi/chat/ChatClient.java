/*
 *    Copyright 2021 Edoardo Vacchi
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

//JAVA 19
//JAVAC_OPTIONS --enable-preview --release 19
//JAVA_OPTIONS  --enable-preview
//SOURCES ../Actor.java
//DEPS com.fasterxml.jackson.core:jackson-databind:2.13.0

package io.github.evacchi.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.evacchi.Actor;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;

import static io.github.evacchi.Actor.*;
import static java.lang.System.*;
import static java.util.concurrent.TimeUnit.*;

public interface ChatClient {

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
