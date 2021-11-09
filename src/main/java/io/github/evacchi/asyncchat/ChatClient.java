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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Scanner;
import java.util.concurrent.Executors;

import static io.github.evacchi.Actor.Stay;
import static java.lang.System.out;

public interface ChatClient {

    record Message(String user, String text) { }

    record NewMessage(String text) { }

    interface Unchecked { Actor.Effect apply(Object msg) throws IOException; }
    static Actor.Behavior Unchecked(Unchecked behavior) {
        return msg -> {
            try { return behavior.apply(msg); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        };
    }

    String host = "localhost";
    int portNumber = 4444;
    ObjectMapper mapper = new ObjectMapper();

    Actor.System system = new Actor.System(Executors.newCachedThreadPool());

    static void main(String[] args) throws IOException, InterruptedException {
        var userName = args[0];

        var channel = AsynchronousSocketChannel.open();
        channel.connect(new InetSocketAddress(host, portNumber), channel, Channels.handler((ignored, chan) -> {
                    var client = system.actorOf(self -> init(self, userName, chan));

                    String line = "";
                    while (!(line = new Scanner(System.in).nextLine()).isBlank()) {
                        client.tell(new NewMessage(line));
                    }
                },
                (exc, b) -> out.println("Failed to connect to server")
        ));

        // just keep the program alive
        while (true) {
            Thread.sleep(1000);
        }
    }

    static Actor.Behavior init(Actor.Address self, String name, AsynchronousSocketChannel channel) {
        var client = system.actorOf(ca -> AsyncChannelActor.idle(ca, self, channel, ""));
        return Unchecked(msg -> switch (msg) {
            case NewMessage nm -> {
                var txtMsg = mapper.writeValueAsString(new Message(name, nm.text));
                client.tell(new AsyncChannelActor.WriteLine(txtMsg));
                yield Stay;
            }
            case AsyncChannelActor.LineRead lr -> {
                var message = mapper.readValue(lr.payload().trim(), Message.class);
                out.printf("%s > %s\n", message.user(), message.text());
                yield Stay;
            }
            default -> throw new RuntimeException("Unhandled message " + msg);
        });
    }

}
