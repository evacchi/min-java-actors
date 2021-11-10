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

package io.github.evacchi.asyncchat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

import static io.github.evacchi.Actor.*;
import static java.lang.System.out;

public interface Channels {
    record Open(Socket channel) { }
    record Error(Throwable throwable) { }
    record ReadBuffer(String content) { }

    String HOST = "localhost";
    int PORT_NUMBER = 4444;
    char END_LINE = '\n';

    class ServerSocket {
        AsynchronousServerSocketChannel socketChannel;

        private ServerSocket(AsynchronousServerSocketChannel socketChannel) { this.socketChannel = socketChannel; }

        static ServerSocket open() throws IOException {
            var socketChannel = AsynchronousServerSocketChannel.open();
            socketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            socketChannel.bind(new InetSocketAddress(HOST, PORT_NUMBER));
            out.printf("Server started at %s.\n", socketChannel.getLocalAddress());
            return new ServerSocket(socketChannel);
        }

        void accept(Address target) {
            socketChannel.accept(null, Channels.handler(
                    (result, ignored) -> {
                        out.println("Child connected!");
                        target.tell(new Open(new Socket(result)));
                    },
                    (exc, ignored) -> target.tell(new Error(exc))));
        }
    }

    class Socket {
        AsynchronousSocketChannel channel;
        Socket(AsynchronousSocketChannel channel) { this.channel = channel; }

        void connect(Address target) {
            channel.connect(new InetSocketAddress(HOST, PORT_NUMBER), this,
                    Channels.handler(
                            (ignored, chan) -> target.tell(new Channels.Open(chan)),
                            (exc, b) -> out.println("Failed to connect to server")));
        }

        void write(String line, Address target) {
            var buf = ByteBuffer.wrap((line + END_LINE).getBytes(StandardCharsets.UTF_8));
            channel.write(buf, channel,
                    Channels.handler(
                            (ignored, ignored_) -> {},
                            (exc, ignored) -> target.tell(new Channels.Error(exc))));
        }

        void read(Address target) {
            var buf = ByteBuffer.allocate(2048);
            channel.read(buf, buf,
                    Channels.handler(
                            (a, buff) -> target.tell(new Channels.ReadBuffer(new String(buff.array()))),
                            (exc, b) -> target.tell(new Channels.Error(exc))));
        }
    }

    interface Actor {
        record LineRead(String payload) { }
        record WriteLine(String payload) { }

        static Behavior socket(Address self, Address parent, Channels.Socket channel) {
            return accumulate(self, parent, channel, "");
        }
        private static Behavior accumulate(Address self, Address parent, Channels.Socket channel, String acc) {
            channel.read(self);

            return msg -> switch (msg) {
                case Channels.Error err -> {
                    err.throwable().printStackTrace();
                    yield Die;
                }
                case Channels.ReadBuffer buffer -> {
                    var line = acc + buffer.content();
                    int eol = line.indexOf(END_LINE);
                    var rem = "";
                    if (eol >= 0) {
                        parent.tell(new Channels.Actor.LineRead(line.substring(0, eol)));
                        rem = line.substring(eol + 2).trim();
                    }
                    yield Become(accumulate(self, parent, channel, rem));
                }
                case WriteLine line -> {
                    channel.write(line.payload(), self);
                    yield Stay;
                }
                default -> throw new RuntimeException("Unhandled message " + msg);
            };
        }

    }


    private static <A,B> CompletionHandler<A, B> handler(BiConsumer<A,B> completed, BiConsumer<Throwable,B> failed) {
        return new CompletionHandler<>() {
            @Override
            public void completed(A result, B attachment) {
                completed.accept(result, attachment);
            }

            @Override
            public void failed(Throwable exc, B attachment) {
                failed.accept(exc, attachment);
            }
        };
    }
}
