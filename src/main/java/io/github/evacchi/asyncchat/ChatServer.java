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
import java.nio.channels.AsynchronousSocketChannel;
import java.util.ArrayList;
import java.util.concurrent.Executors;

import static io.github.evacchi.Actor.*;
import static java.lang.System.out;

public interface ChatServer {
    record SocketOpen(AsynchronousSocketChannel channel) { }
    record SocketError() { }
    record ClientConnected(Address addr) { }

    Actor.System system = new Actor.System(Executors.newCachedThreadPool());

    static void main(String... args) throws IOException, InterruptedException {
        var socketChannel = AsynchronousServerSocketChannel.open();
        socketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        socketChannel.bind(new InetSocketAddress(Constants.HOST, Constants.PORT_NUMBER));
        out.printf("Server started at %s.\n", socketChannel.getLocalAddress());

        var childrenManager = system.actorOf(self -> childrenManager(self));
        system.actorOf(self -> serverSocketHandler(self, childrenManager, () ->
                socketChannel.accept(null, Channels.handler(
                    (result, ignored) -> {
                        out.println("Child connected!");
                        self.tell(new SocketOpen(result));
                    },
                    (exc, ignored) -> self.tell(new SocketError()))
        )));

        // keep it running
        while (true) {
            Thread.sleep(Integer.MAX_VALUE);
        }
    }

    static Behavior serverSocketHandler(Address self, Address childrenManager, Runnable acceptConnection) {
        out.println("Server in open socket!");
        acceptConnection.run();

        return msg -> switch (msg) {
            case SocketError ignored -> throw new RuntimeException("Failed to open the socket");
            case SocketOpen open -> {
                var child = system.actorOf(ca ->
                        AsyncChannelActor.idle(ca, childrenManager, new AsyncChannelActor.ChannelWrapper(open.channel()), ""));
                childrenManager.tell(new ClientConnected(child));

                yield Become(serverSocketHandler(self, childrenManager, acceptConnection));
            }
            default -> throw new RuntimeException("Unhandled message " + msg);
        };
    }

    static Behavior childrenManager(Address self) {
        var children = new ArrayList<Address>();

        return msg -> {
            switch (msg) {
                case ClientConnected cc -> children.add(cc.addr());
                case AsyncChannelActor.LineRead lr -> children.forEach(child ->
                        child.tell(new AsyncChannelActor.WriteLine(lr.payload()))
                );
                default -> throw new RuntimeException("Unhandled message " + msg);
            }
            return Stay;
        };
    }

}
