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
//DEPS com.fasterxml.jackson.core:jackson-databind:2.13.0

package io.github.evacchi.asyncchat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.evacchi.Actor;
import io.github.evacchi.Actor.Address;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Scanner;
import java.util.concurrent.Executors;

import static io.github.evacchi.Actor.Become;
import static io.github.evacchi.Actor.Stay;
import static java.lang.System.err;
import static java.lang.System.out;

public interface ChatClient {
    record Message(String user, String text) { }

    interface IOBehavior {  Actor.Effect apply(Object msg) throws IOException; }
    static Actor.Behavior IO(IOBehavior behavior) {
        return msg -> {
            try { return behavior.apply(msg); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        };
    }

    Actor.System system = new Actor.System(Executors.newCachedThreadPool());

    static void main(String[] args) throws IOException {
        var userName = args[0];

        var channel = new Channels.Socket(AsynchronousSocketChannel.open());
        var client = system.actorOf(self -> clientConnecting(self, channel));

        var in = new Scanner(System.in);
        while (true) {
            var line = in.nextLine();
            if (line != null && !line.isBlank()) {
                client.tell(new Message(userName, line));
            }
        }
    }

    static Actor.Behavior clientConnecting(Address self, Channels.Socket channel) {
        channel.connect(self);
        return msg -> switch (msg) {
            case Channels.Open co -> {
                var socket = system.actorOf(ca -> Channels.Actor.socket(ca, self, channel));
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

        return IO(msg -> switch (msg) {
            case Message m -> {
                var jsonMsg = mapper.writeValueAsString(m);
                socket.tell(new Channels.Actor.WriteLine(jsonMsg));
                yield Stay;
            }
            case Channels.Actor.LineRead lr -> {
                var message = mapper.readValue(lr.payload().trim(), Message.class);
                out.printf("%s > %s\n", message.user(), message.text());
                yield Stay;
            }
            default -> throw new RuntimeException("Unhandled message " + msg);
        });
    }

}
