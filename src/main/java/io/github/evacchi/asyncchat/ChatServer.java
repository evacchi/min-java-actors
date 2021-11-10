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

//JAVA 17
//JAVAC_OPTIONS --enable-preview --release 17
//JAVA_OPTIONS  --enable-preview
//SOURCES ../Actor.java
//SOURCES ./Channels.java

package io.github.evacchi.asyncchat;

import io.github.evacchi.Actor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executors;

import static io.github.evacchi.Actor.*;
import static java.lang.System.out;

public interface ChatServer {
    record ClientConnected(Address addr) { }

    Actor.System system = new Actor.System(Executors.newCachedThreadPool());

    static void main(String... args) throws IOException {
        var clientManager = system.actorOf(self -> clientManager(self));

        var serverSocket = Channels.ServerSocket.open();
        system.actorOf(self -> serverSocketHandler(self, clientManager, serverSocket));

        // deadlock on the main thread to avoid Maven killing the process
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) { }
    }

    static Behavior serverSocketHandler(Address self, Address childrenManager, Channels.ServerSocket serverSocketWrapper) {
        out.println("Server in open socket!");

        var accept = serverSocketWrapper
                .accept()
                .thenAccept(c -> self.tell(new Channels.Open(c)))
                .exceptionally(exc ->  {
                    out.println("Failed to open the socket");
                    return null;
                });

        return msg -> switch (msg) {
            case Channels.Open open -> {
                var client =
                        system.actorOf(ca -> Channels.SocketActor.socket(ca, childrenManager, open.channel()));
                childrenManager.tell(new ClientConnected(client));

                accept.cancel(true);
                yield Become(serverSocketHandler(self, childrenManager, serverSocketWrapper));
            }
            default -> throw new RuntimeException("Unhandled message " + msg);
        };
    }

    static Behavior clientManager(Address self) {
        var clients = new ArrayList<Address>();
        return msg -> {
            switch (msg) {
                case ClientConnected cc -> clients.add(cc.addr());
                case Channels.SocketActor.LineRead lr ->
                        clients.forEach(client -> client.tell(new Channels.SocketActor.WriteLine(lr.payload())));
                default -> throw new RuntimeException("Unhandled message " + msg);
            }
            return Stay;
        };
    }

}
