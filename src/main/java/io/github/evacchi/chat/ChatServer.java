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

package io.github.evacchi.chat;

import io.github.evacchi.Actor;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.*;

import static io.github.evacchi.Actor.*;
import static java.lang.System.*;
import static java.util.concurrent.TimeUnit.*;

public interface ChatServer {
    interface IOBehavior { Actor.Effect apply(Object msg) throws IOException; }
    static Actor.Behavior IO(IOBehavior behavior) {
        return msg -> {
            try { return behavior.apply(msg); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        };
    }
    static Object Poll = new Object();
    record ServerMessage(String payload) {}
    record CreateClient(Socket socket) {}

    int portNumber = 4444;

    Actor.System sys = new Actor.System(Executors.newCachedThreadPool());
    Actor.System io = new Actor.System(Executors.newFixedThreadPool(2));
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

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
        return IO(msg -> {
            switch (msg) {
                // create a connection handler and a message handler for that client
                case CreateClient n -> {
                    var socket = n.socket();
                    out.println("accepts : " + socket.getRemoteSocketAddress());

                    var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    var clientInput =
                            io.actorOf(me -> clientInput(me, self, in));

                    var writer = new PrintWriter(socket.getOutputStream(), true);
                    var clientOutput =
                            sys.actorOf(client -> clientOutput(writer));

                    clients.add(clientOutput);
                    return Stay;
                }
                // broadcast the message to all connected clients
                case ServerMessage m -> {
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
        return IO(msg -> {
            if (msg != Poll) return Stay;
            var socket = serverSocket.accept();
            clientManager.tell(new CreateClient(socket));
            return Become(serverSocketHandler(self, clientManager, serverSocket));
        });
    }

    static Behavior clientInput(Address self, Address clientManager, BufferedReader in) {
        // schedule a message to self
        scheduler.schedule(() -> self.tell(Poll), 100, MILLISECONDS);

        return IO(msg -> {
            // ignore non-Poll messages
            if (msg != Poll) return Stay;
            if (in.ready()) {
                var m = in.readLine();
                // log message to stdout
                out.println(m);
                // broadcast to all other clients
                clientManager.tell(new ServerMessage(m));
            }

            // "stay" in the same state, ensuring that the initializer is re-evaluated
            return Become(clientInput(self, clientManager, in));
        });
    }

    static Behavior clientOutput(PrintWriter writer) {
        return msg -> {
            if (msg instanceof ServerMessage m) writer.println(m.payload());
            return Stay;
        };
    }
}
