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

package io.github.evacchi.channels;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public interface Channels {

    class ServerSocket {
        AsynchronousServerSocketChannel socketChannel;
        private ServerSocket(AsynchronousServerSocketChannel socketChannel) { this.socketChannel = socketChannel; }

        public static ServerSocket open(String host, int port) throws IOException {
            var socketChannel = AsynchronousServerSocketChannel.open();
            socketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            socketChannel.bind(new InetSocketAddress(host, port));
            System.out.printf("Server started at %s.\n", socketChannel.getLocalAddress());
            return new ServerSocket(socketChannel);
        }

        public CompletableFuture<Socket> accept() {
            var f = new CompletableFuture<AsynchronousSocketChannel>();
            socketChannel.accept(null, handleResult(f));
            return f.thenApply(Socket::new);
        }

        public SocketAddress address() {
            try {
                return socketChannel.getLocalAddress();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    class Socket {
        AsynchronousSocketChannel channel;
        Socket(AsynchronousSocketChannel channel) { this.channel = channel; }

        public static Socket open() throws IOException {
            return new Socket(AsynchronousSocketChannel.open());
        }

        public SocketAddress localAddress() {
            try {
                return channel.getLocalAddress();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public SocketAddress remoteAddress() {
            try {
                return channel.getRemoteAddress();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public CompletableFuture<Socket> connect(String host, int port) {
            var f = new CompletableFuture<Socket>();
            channel.connect(new InetSocketAddress(host, port), this, handleAttachment(f));
            return f;
        }

        public CompletableFuture<Void> write(byte[] bytes) {
            var f = new CompletableFuture<Void>();
            var buf = ByteBuffer.wrap(bytes);
            channel.write(buf, null, handleAttachment(f));
            return f;
        }

        public CompletableFuture<String> read() {
            var f = new CompletableFuture<ByteBuffer>();
            var buf = ByteBuffer.allocate(2048);
            channel.read(buf, buf, handleAttachment(f));
            return f.thenApply(bb -> new String(bb.array()));
        }
    }

    private static <A, B> CompletionHandler<A, B> handleAttachment(CompletableFuture<B> f) {
        return new CompletionHandler<>() {
            public void completed(A result, B attachment) { f.complete(attachment); }
            public void failed(Throwable exc, B attachment) { f.completeExceptionally(exc); }
        };
    }

    private static <A, B> CompletionHandler<A, B> handleResult(CompletableFuture<A> f) {
        return new CompletionHandler<>() {
            public void completed(A result, B attachment) { f.complete(result); }
            public void failed(Throwable exc, B attachment) { f.completeExceptionally(exc); }
        };
    }
}
