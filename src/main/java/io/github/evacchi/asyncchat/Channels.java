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
import java.util.concurrent.CompletableFuture;
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

        CompletableFuture<Socket> accept() {
            var f = new CompletableFuture<Socket>();
            socketChannel.accept(null, Channels.handler(
                    (result, ignored) -> f.complete(new Socket(result)),
                    (exc, ignored) -> f.completeExceptionally(exc)));
            return f;
        }
    }

    class Socket {
        AsynchronousSocketChannel channel;
        Socket(AsynchronousSocketChannel channel) { this.channel = channel; }

        CompletableFuture<Socket> connect() {
            var f = new CompletableFuture<Socket>();
            channel.connect(new InetSocketAddress(HOST, PORT_NUMBER), this,
                    Channels.handler(
                            (ignored, chan) -> f.complete(chan),
                            (exc, ignored) -> f.completeExceptionally(exc)));
            return f;
        }

        CompletableFuture<Void> write(String line) {
            var f = new CompletableFuture<Void>();
            var buf = ByteBuffer.wrap((line + END_LINE).getBytes(StandardCharsets.UTF_8));
            channel.write(buf, channel,
                    Channels.handler(
                            (ignored, ignored_) -> f.complete(null),
                            (exc, ignored) -> f.completeExceptionally(exc)));
            return f ;
        }

        CompletableFuture<String> read() {
            var f = new CompletableFuture<String>();
            var buf = ByteBuffer.allocate(2048);
            channel.read(buf, buf,
                    Channels.handler(
                            (a, buff) -> f.complete(new String(buff.array())),
                            (exc, b) -> f.completeExceptionally(exc)));
            return f;
        }
    }

    interface Actor {
        record LineRead(String payload) { }
        record WriteLine(String payload) { }

        static Behavior socket(Address self, Address parent, Channels.Socket channel) {
            return accumulate(self, parent, channel, "");
        }
        private static Behavior accumulate(Address self, Address parent, Channels.Socket channel, String acc) {
            channel.read()
                    .thenAccept(s -> self.tell(new Channels.ReadBuffer(s)))
                    .exceptionally(err -> { err.printStackTrace(); return null; });

            return msg -> switch (msg) {
                case Channels.ReadBuffer buffer -> {
                    var line = acc + buffer.content();
                    int eol = line.indexOf(END_LINE);
                    if (eol >= 0) {
                        parent.tell(new Channels.Actor.LineRead(line.substring(0, eol)));
                        yield Become(accumulate(self, parent, channel, line.substring(eol + 2).trim()));
                    } else yield Become(socket(self, parent, channel));
                }
                case WriteLine line -> {
                    channel.write(line.payload());
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
