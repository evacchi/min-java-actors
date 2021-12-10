/*
 *    Copyright 2021 Andrea Peruffo
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

//JAVA 17
//JAVAC_OPTIONS --enable-preview --release 17
//JAVA_OPTIONS  --enable-preview
//REPOS jitpack=https://jitpack.io/
//DEPS com.github.evacchi:java-async-channels:main-SNAPSHOT
//DEPS com.github.evacchi:min-java-actors:main-SNAPSHOT
//SOURCES ChannelActor.java

package io.github.evacchi.asyncchat;

import io.github.evacchi.Actor;
import io.github.evacchi.channels.Channels;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executors;

import static io.github.evacchi.Actor.*;
import static java.lang.System.*;

public interface ChatServer {
    record ClientConnection(Channels.Socket socket) { }
    record ClientConnected(Address addr) { }

    Actor.System system = new Actor.System(Executors.newVirtualThreadPerTaskExecutor());
    String HOST = "localhost"; int PORT = 4444;

    static void main(String... args) throws IOException, InterruptedException {
        var serverSocket = Channels.ServerSocket.open(HOST, PORT);

        var clientManager =
                system.actorOf(self -> clientManager(self));
        var serverSocketHandler =
                system.actorOf(self -> serverSocketHandler(self, clientManager, serverSocket));

        Thread.currentThread().join();
    }

    static Behavior serverSocketHandler(Address self, Address childrenManager, Channels.ServerSocket serverSocket) {
        serverSocket.accept()
                .thenAccept(skt -> self.tell(new ClientConnection(skt)))
                .exceptionally(exc -> { exc.printStackTrace(); return null; });

        return msg -> switch (msg) {
            case ClientConnection conn -> {
                out.printf("Client connected at %s\n", conn.socket().remoteAddress());
                var client =
                        system.actorOf(ca -> ChannelActor.socketHandler(ca, childrenManager, conn.socket()));
                childrenManager.tell(new ClientConnected(client));

                yield Become(serverSocketHandler(self, childrenManager, serverSocket));
            }
            default -> throw new RuntimeException("Unhandled message " + msg);
        };
    }

    static Behavior clientManager(Address self) {
        var clients = new ArrayList<Address>();
        return msg -> {
            switch (msg) {
                case ClientConnected cc -> clients.add(cc.addr());
                case ChannelActor.LineRead lr ->
                        clients.forEach(client -> client.tell(new ChannelActor.WriteLine(lr.payload())));
                default -> throw new RuntimeException("Unhandled message " + msg);
            }
            return Stay;
        };
    }

}
