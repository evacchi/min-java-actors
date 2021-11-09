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
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static io.github.evacchi.Actor.*;
import static java.lang.System.out;

public interface ChatServer {
    record SocketOpen(Actor.Address child) { }
    record SocketError() { }

    Actor.System system = new Actor.System(Executors.newCachedThreadPool());

    static void main(String... args) throws IOException, InterruptedException {
        var socketChannel = AsynchronousServerSocketChannel.open();
        socketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        socketChannel.bind(new InetSocketAddress(Constants.HOST, Constants.PORT_NUMBER));
        out.printf("Server started at %s.\n", socketChannel.getLocalAddress());

        system.actorOf(self -> idle(self, socketChannel, new ArrayList<>()));

        // Just keep the thread alive
        while (true) {
            Thread.sleep(Integer.MAX_VALUE);
        }
    }

    static Behavior idle(Address self, AsynchronousServerSocketChannel channel, List<Address> children) {
        out.println("Server in open socket!");
        channel.accept(null, Channels.handler(
                (result, ignored) -> {
                    out.println("Child connected!");
                    var child = system.actorOf(ca -> AsyncChannelActor.idle(ca, self, result, ""));
                    self.tell(new SocketOpen(child));
                },
                (exc, ignored) -> self.tell(new SocketError()))
        );

        return msg -> switch (msg) {
            case SocketError ignored -> throw new RuntimeException("Failed to open the socket");
            case SocketOpen open -> {
                children.add(open.child());
                yield Become(idle(self, channel, children));
            }
            case AsyncChannelActor.LineRead lr -> {
                children.forEach(child ->
                        child.tell(new AsyncChannelActor.WriteLine(lr.payload()))
                );
                yield Stay;
            }
            default -> throw new RuntimeException("Unhandled message " + msg);
        };
    }

}
