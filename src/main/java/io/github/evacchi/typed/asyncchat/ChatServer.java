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
//SOURCES ../../channels/Channels.java
//SOURCES ChannelActor.java
//SOURCES ../../TypedActor.java

package io.github.evacchi.typed.asyncchat;

import io.github.evacchi.TypedActor;
import io.github.evacchi.channels.Channels;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static io.github.evacchi.TypedActor.*;

public interface ChatServer {
    record ClientConnection(Channels.Socket socket) {};

    sealed interface ClientManagerProtocol {};
    record ClientConnected(Address<ChannelActor.ChannelProtocol> addr) implements ClientManagerProtocol {};
    record LineRead(String payload) implements ClientManagerProtocol {};

    TypedActor.System system = new TypedActor.System(Executors.newCachedThreadPool());
    String HOST = "localhost"; int PORT = 4444;

    static void main(String... args) throws IOException, InterruptedException {
        var serverSocket = Channels.ServerSocket.open(HOST, PORT);

        var clientManagerActor = new ClientManager();
        Address<ClientManagerProtocol> clientManager =
                system.actorOf(self -> msg -> clientManagerActor.apply(msg));
        var serverSocketActor = new ServerSocket(clientManager, serverSocket);
        Address<ClientConnection> serverSocketHandler =
                system.actorOf(self -> serverSocketActor.handler(self));

        Thread.currentThread().join();
    }

    class ServerSocket {
        private final Address<ClientManagerProtocol> childrenManager;
        private final Channels.ServerSocket serverSocket;

        public ServerSocket(Address<ClientManagerProtocol> childrenManager, Channels.ServerSocket serverSocket) {
            this.childrenManager = childrenManager;
            this.serverSocket = serverSocket;
        }

        Behavior<ClientConnection> handler(Address<ClientConnection> self) {
            serverSocket.accept()
                    .thenAccept(skt -> self.tell(new ClientConnection(skt)))
                    .exceptionally(exc -> { exc.printStackTrace(); return null; });

            return msg -> handler(self, msg);
        }

        Effect<ClientConnection> handler(Address<ClientConnection> self, ClientConnection conn) {
            var channelActor = new ChannelActor<>(childrenManager, line -> new LineRead(line), conn.socket());
            Address<ChannelActor.ChannelProtocol> client =
                    system.actorOf(s -> channelActor.socketHandler(s));
            childrenManager.tell(new ClientConnected(client));

            return Become(handler(self));
        }
    }

    class ClientManager {

        private final List<Address<ChannelActor.ChannelProtocol>> clients;

        public ClientManager() {
            this.clients = new ArrayList<>();
        }

        Effect<ClientManagerProtocol> apply(ClientManagerProtocol msg) {
            switch (msg) {
                case ClientConnected cc -> clients.add(cc.addr());
                case LineRead lr ->
                    clients.forEach(client -> client.tell(new ChannelActor.WriteLine(lr.payload())));
            }
            return Stay();
        }
    }

}
