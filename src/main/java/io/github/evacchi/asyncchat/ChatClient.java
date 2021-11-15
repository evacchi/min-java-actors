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
//REPOS mavencentral,jitpack=https://jitpack.io/
//DEPS com.github.evacchi:min-java-actors:main-SNAPSHOT
//DEPS com.github.evacchi:java-async-channels:main-SNAPSHOT
//DEPS com.fasterxml.jackson.core:jackson-databind:2.13.0
//SOURCES ChannelActor.java

package io.github.evacchi.asyncchat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.evacchi.Actor;
import io.github.evacchi.channels.Channels;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Scanner;
import java.util.concurrent.Executors;

import static io.github.evacchi.Actor.*;
import static java.lang.System.*;

public interface ChatClient {
    record ClientConnection(Channels.Socket socket) { }
    record Message(String user, String text) {}

    Actor.System system = new Actor.System(Executors.newCachedThreadPool());
    String HOST = "localhost"; int PORT = 4444;

    static void main(String[] args) throws IOException {
        var userName = args[0];

        var channel = Channels.Socket.open();
        var client = system.actorOf(self -> clientConnecting(self, channel));

        out.printf("Login...............%s\n", userName);

        var scann = new Scanner(in);
        while (true) {
            var line = scann.nextLine();
            if (line != null && !line.isBlank()) {
                client.tell(new Message(userName, line));
            }
        }
    }

    static Actor.Behavior clientConnecting(Address self, Channels.Socket channel) {
        channel.connect(HOST, PORT)
                .thenAccept(skt -> self.tell(new ClientConnection(skt)))
                .exceptionally(err -> { err.printStackTrace(); return null; });
        return msg -> switch (msg) {
            case ClientConnection conn -> {
                out.printf("Local connection....%s\n", conn.socket().localAddress());
                out.printf("Remote connection...%s\n", conn.socket().remoteAddress());
                var socket =
                        system.actorOf(ca -> ChannelActor.socketHandler(ca, self, conn.socket()));
                yield Become(clientReady(self, socket));
            }
            case Message m -> {
                err.println("Socket not connected");
                yield Stay;
            }
            default -> throw new RuntimeException("Unhandled message " + msg);
        };
    }

    static Actor.Behavior clientReady(Address self, Address socket) {
        var mapper = new ObjectMapper();

        return msg -> {
            try {
                switch (msg) {
                    case Message m -> {
                        var jsonMsg = mapper.writeValueAsString(m);
                        socket.tell(new ChannelActor.WriteLine(jsonMsg));
                    }
                    case ChannelActor.LineRead lr -> {
                        var message = mapper.readValue(lr.payload().trim(), Message.class);
                        out.printf("%s > %s\n", message.user(), message.text());
                    }
                    default -> throw new RuntimeException("Unhandled message " + msg);
                }
                return Stay;
            } catch(JsonProcessingException e) { throw new UncheckedIOException(e); }
        };
    }

}
