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
//SOURCES ../../TypedLoomActor.java
//DEPS com.fasterxml.jackson.core:jackson-databind:2.13.0

package io.github.evacchi.typed.loomchat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.evacchi.TypedLoomActor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.util.Scanner;

import static io.github.evacchi.TypedLoomActor.*;
import static java.lang.System.in;
import static java.lang.System.out;

public interface ChatClient {

    String host = "localhost";
    int portNumber = 4444;
    TypedLoomActor.System system = new TypedLoomActor.System();

    sealed interface ClientProtocol { }
    record Message(String user, String text) implements ClientProtocol {}
    record LineRead(String payload) implements ClientProtocol {}

    static void main(String[] args) throws IOException {
        var userName = args[0];

        var socket = new Socket(host, portNumber);
        var channel = new ChannelActors(socket);
        Address<ChannelActors.WriteLine> writer = system.actorOf(self -> msg -> channel.writer(msg));
        Address<ClientProtocol> client = system.actorOf(self -> msg -> client(writer, msg));
        Address<ChannelActors.PerformReadLine> reader = system.actorOf(self -> msg -> channel.reader(self, client, (line) -> new LineRead(line), msg));
        reader.tell(new ChannelActors.PerformReadLine());

        out.printf("Login............... %s\n", userName);

        var scann = new Scanner(in);
        while (true) {
            var line = scann.nextLine();
            if (line != null && !line.isBlank()) {
                client.tell(new Message(userName, line));
            }
        }
    }

    static Effect<ClientProtocol> client(Address<ChannelActors.WriteLine> writer, ClientProtocol msg) {
        var mapper = new ObjectMapper();

        try {
            switch (msg) {
                case Message m -> {
                    var jsonMsg = mapper.writeValueAsString(m);
                    writer.tell(new ChannelActors.WriteLine(jsonMsg));
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
