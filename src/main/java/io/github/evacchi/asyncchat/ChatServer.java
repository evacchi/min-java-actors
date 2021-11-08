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

import java.io.*;
import java.net.*;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.ArrayList;
import java.util.concurrent.*;

import static io.github.evacchi.Actor.*;
import static java.lang.System.*;

public interface ChatServer {
    sealed interface Protocol {};
    record SocketOpen(Actor.Address child) implements Protocol {}
    record SocketError() implements Protocol {}

    String host = "localhost";
    int portNumber = 4444;

    Actor.System system = new Actor.System(Executors.newCachedThreadPool());

    static void main(String... args) throws IOException, InterruptedException {
        var socketChannel = AsynchronousServerSocketChannel.open();
        socketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        socketChannel.bind(new InetSocketAddress(host, portNumber));
        out.printf("Server started at %s.\n", socketChannel.getLocalAddress());

        var serverSocketHandler =
                system.actorOf(self -> idle(self, socketChannel));

        // Just keep the thread alive
        while (true) {
            Thread.sleep(1000);
        }
    }

    static Behavior idle(Address self, AsynchronousServerSocketChannel channel) {
        out.println("Server in open socket!");
        var children = new ArrayList<Actor.Address>();
        channel.accept(null, Channels.onAccept(
                result -> {
                    var child = system.actorOf(ca -> AsyncChannelActor.idle(ca, self, result, ""));
                    self.tell(new SocketOpen(child));
                },
                exc -> self.tell(new SocketError()))
        );

        return msg -> {
            switch (msg) {
                case SocketError ignored -> throw new RuntimeException("Failed to open the socket");
                case SocketOpen open -> children.add(open.child);
                case AsyncChannelActor.LineRead lr -> {
                    for (var child: children) {
                        child.tell(new AsyncChannelActor.WriteLine(lr.payload()));
                    }
                }
                default -> throw new RuntimeException("Unhandled message " + msg);
            }
            return Stay;
        };
    }

}
