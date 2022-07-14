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
//SOURCES ChannelActor.java
//SOURCES ../../TypedActor.java
//DEPS com.fasterxml.jackson.core:jackson-databind:2.13.0

package io.github.evacchi.typed.asyncchat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.evacchi.TypedActor;
import io.github.evacchi.channels.Channels;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Scanner;
import java.util.concurrent.Executors;

import static io.github.evacchi.TypedActor.*;
import static java.lang.System.*;

public interface ChatClient {
    sealed interface ClientProtocol {};
    record ClientConnection(Channels.Socket socket) implements ClientProtocol {};
    record Message(String user, String text) implements ClientProtocol {};
    record LineRead(String payload) implements ClientProtocol {};

    TypedActor.System system = new TypedActor.System(Executors.newCachedThreadPool());
    String HOST = "localhost"; int PORT = 4444;

    static void main(String[] args) throws IOException {
        var userName = args[0];

        var channel = Channels.Socket.open();
        Address<ClientProtocol> client = system.actorOf(self -> msg -> clientConnecting(self, msg));
        channel.connect(HOST, PORT)
                .thenAccept(skt -> client.tell(new ClientConnection(skt)))
                .exceptionally(err -> { err.printStackTrace(); return null; });

        out.printf("Login...............%s\n", userName);

        var scann = new Scanner(in);
        while (true) {
            var line = scann.nextLine();
            if (line != null && !line.isBlank()) {
                client.tell(new Message(userName, line));
            }
        }
    }

    static Effect<ClientProtocol> clientConnecting(Address<ClientProtocol> self, ClientProtocol msg) {
        return switch (msg) {
            case ClientConnection conn -> {
                out.printf("Local connection....%s\n", conn.socket().localAddress());
                out.printf("Remote connection...%s\n", conn.socket().remoteAddress());
                var channelActor = new ChannelActor<>(self, line -> new LineRead(line), conn.socket());
                Address<ChannelActor.ChannelProtocol> socket =
                        system.actorOf(s -> channelActor.socketHandler(s));
                yield Become(m -> clientReady(socket, m));
            }
            default -> {
                err.println("Socket not connected " + msg);
                yield Stay();
            }
        };
    }

    static Effect<ClientProtocol> clientReady(Address<ChannelActor.ChannelProtocol> socket, ClientProtocol msg) {
        var mapper = new ObjectMapper();

        try {
            switch (msg) {
                case ClientConnection conn -> { /* ignore */ }
                case Message m -> {
                    var jsonMsg = mapper.writeValueAsString(m);
                    socket.tell(new ChannelActor.WriteLine(jsonMsg));
                }
                case LineRead lr -> {
                    var message = mapper.readValue(lr.payload().trim(), Message.class);
                    out.printf("%s > %s\n", message.user(), message.text());
                }
            }
            return Stay();
        } catch(JsonProcessingException e) { throw new UncheckedIOException(e); }
    }

}
