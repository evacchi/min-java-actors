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
//REPOS jitpack=https://jitpack.io/
//DEPS com.github.evacchi:min-java-actors:main-SNAPSHOT

package io.github.evacchi.asyncchat;

import io.github.evacchi.Actor;

import java.io.IOException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
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
    }

    static Behavior serverSocketHandler(Address self, Address childrenManager, Channels.ServerSocket serverSocketWrapper) {
        out.println("Server in open socket!");
        serverSocketWrapper.accept(self);

        return msg -> switch (msg) {
            case Channels.Error ignored -> throw new RuntimeException("Failed to open the socket");
            case Channels.Open open -> {
                var client =
                        system.actorOf(ca -> Channels.Actor.socket(ca, childrenManager, open.channel()));
                childrenManager.tell(new ClientConnected(client));

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
                case Channels.Actor.LineRead lr ->
                        clients.forEach(client -> client.tell(new Channels.Actor.WriteLine(lr.payload())));
                default -> throw new RuntimeException("Unhandled message " + msg);
            }
            return Stay;
        };
    }

}
