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

//JAVA 19
//JAVAC_OPTIONS --enable-preview --release 19
//JAVA_OPTIONS  --enable-preview
//SOURCES ChannelActors.java
//SOURCES ../../TypedLoomActor.java

package io.github.evacchi.typed.loomchat;

import io.github.evacchi.TypedLoomActor;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import static io.github.evacchi.TypedLoomActor.*;
import static java.lang.System.out;

public interface ChatServer {

    sealed interface ClientManagerProtocol { }
    record ClientConnected(Address addr) implements ClientManagerProtocol { }
    record LineRead(String payload) implements ClientManagerProtocol {}

    TypedLoomActor.System system = new TypedLoomActor.System();
    int PORT = 4444;

    static void main(String... args) throws IOException, InterruptedException {
        var serverSocket = new ServerSocket(PORT);
        out.printf("Server started at %s.\n", serverSocket.getLocalSocketAddress());

        Address<ClientManagerProtocol> clientManager =
                system.actorOf(self -> msg -> clientManager(msg));

        while (true) {
            var socket = serverSocket.accept();
            var channel = new ChannelActors(socket);
            ChannelActors.Reader<ClientManagerProtocol> reader =
                    channel.reader(clientManager, (line) -> new LineRead(line));
            reader.start(system.actorOf(self -> msg -> reader.read(self)));
            Address<ChannelActors.WriteLine> writer = system.actorOf(self -> msg -> channel.writer(msg));
            clientManager.tell(new ClientConnected(writer));
        }
    }

    static Effect<ClientManagerProtocol> clientManager(ClientManagerProtocol msg) {
        return clientManager(msg, new ArrayList());
    }

    static Effect<ClientManagerProtocol> clientManager(ClientManagerProtocol msg, List<Address<ChannelActors.WriteLine>> clients) {
        return switch (msg) {
            case ClientConnected(var address) -> {
                clients.add(address);
                yield Become(m -> clientManager(m, clients));
            }
            case LineRead(var payload) -> {
                clients.forEach(client -> client.tell(new ChannelActors.WriteLine(payload)));
                yield Stay();
            }
        };
    }

}
