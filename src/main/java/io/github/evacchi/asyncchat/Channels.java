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
import java.util.function.Consumer;

import static io.github.evacchi.Actor.*;
import static java.lang.System.out;

public interface Channels {
    record Open(Socket channel) { }
    record Error(Throwable throwable) { }
    record ReadBuffer(String content) { }

    String HOST = "localhost";
    int PORT_NUMBER = 4444;
    char END_LINE = '\n';

    private static <A,B> CompletionHandler<A, B> handler(Consumer<A> completed, Consumer<Throwable> failed) {
        return new CompletionHandler<>() {
            @Override
            public void completed(A result, B ignored) {
                completed.accept(result);
            }

            @Override
            public void failed(Throwable exc, B ignored) {
                failed.accept(exc);
            }
        };
    }

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
            var future = new CompletableFuture<Socket>();

            socketChannel.accept(null, Channels.handler(
                    (result) -> {
                        out.println("Child connected!");
                        future.complete(new Socket(result));
                    },
                    (exc) -> future.completeExceptionally(exc)
            ));

            return future;
        }

    }

    class Socket {
        AsynchronousSocketChannel channel;
        Socket(AsynchronousSocketChannel channel) { this.channel = channel; }

        CompletableFuture<Socket> connect() {
            var future = new CompletableFuture<Socket>();

            channel.connect(new InetSocketAddress(HOST, PORT_NUMBER), null,
                    Channels.handler(
                            (chan) -> future.complete(this),
                            (exc) -> future.completeExceptionally(exc)));

            return future;
        }

        CompletableFuture<Void> write(String line) {
            var future = new CompletableFuture<Void>();

            var buf = ByteBuffer.wrap((line + END_LINE).getBytes(StandardCharsets.UTF_8));
            channel.write(buf, channel,
                    Channels.handler(
                            (ignored) -> future.complete(null),
                            (exc) -> future.completeExceptionally(exc)));

            return future;
        }

        CompletableFuture<String> read() {
            var future = new CompletableFuture<String>();

            var buf = ByteBuffer.allocate(2048);
            channel.read(buf, null,
                    Channels.handler(
                            (ignored) -> future.complete(new String(buf.array())),
                            (exc) -> future.completeExceptionally(exc)));

            return future;
        }
    }

    interface SocketActor {
        record LineRead(String payload) { }
        record WriteLine(String payload) { }

        static Behavior socket(Address self, Address parent, Channels.Socket channel) {
            return accumulate(self, parent, channel, "");
        }
        private static Behavior accumulate(Address self, Address parent, Channels.Socket channel, String acc) {
            channel
                    .read()
                    .thenAccept(str -> self.tell(new Channels.ReadBuffer(str)))
                    .exceptionally(exc -> {
                        self.tell(new Channels.Error(exc));
                        return null;
                    });

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
                        parent.tell(new SocketActor.LineRead(line.substring(0, eol)));
                        rem = line.substring(eol + 2).trim();
                    }
                    yield Become(accumulate(self, parent, channel, rem));
                }
                case WriteLine line -> {
                    channel.write(line.payload());
                    yield Stay;
                }
                default -> throw new RuntimeException("Unhandled message " + msg);
            };
        }
    }
}
