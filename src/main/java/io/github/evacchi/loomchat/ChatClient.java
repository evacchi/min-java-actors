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

//JAVA 19
//JAVAC_OPTIONS --enable-preview --release 19
//JAVA_OPTIONS  --enable-preview
//SOURCES ChannelActors.java
//SOURCES ../LoomActor.java
//DEPS com.fasterxml.jackson.core:jackson-databind:2.13.0

package io.github.evacchi.loomchat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.evacchi.LoomActor;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

import static io.github.evacchi.LoomActor.*;
import static java.lang.System.*;

public interface ChatClient {

    String host = "localhost";
    int portNumber = 4444;
    LoomActor.System system = new LoomActor.System();

    record Message(String user, String text) {}

    static void main(String[] args) throws IOException {
        var userName = args[0];

        var socket = new Socket(host, portNumber);
        var channel = new ChannelActors(socket);
        var writer = system.actorOf(self -> channel.writer());
        var client = system.actorOf(self -> client(self, writer));
        system.actorOf(self -> channel.reader(self, client));

        out.printf("Login............... %s\n", userName);

        var scann = new Scanner(in);
        while (true) {
            switch (scann.nextLine()) {
                case String line when (line != null && !line.isBlank()) ->
                    client.tell(new Message(userName, line));
                default -> {}
            }
        }
    }

    static LoomActor.Behavior client(Address self, Address writer) {
        var mapper = new ObjectMapper();

        return msg -> {
            try {
                switch (msg) {
                    case Message m -> {
                        var jsonMsg = mapper.writeValueAsString(m);
                        writer.tell(new ChannelActors.WriteLine(jsonMsg));
                    }
                    case ChannelActors.LineRead(var payload) -> {
                        switch (mapper.readValue(payload.trim(), Message.class)) {
                            case Message(var user, var text) -> out.printf("%s > %s\n", user, text);
                        }
                    }
                    default -> throw new RuntimeException("Unhandled message " + msg);
                }
                return Stay;
            } catch(JsonProcessingException e) { throw new UncheckedIOException(e); }
        };
    }

}
