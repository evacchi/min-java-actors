package io.github.evacchi.asyncchat;

import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.function.Consumer;

public interface Channels {
    static CompletionHandler<Void, AsynchronousSocketChannel> onConnect(Consumer<AsynchronousSocketChannel> completed, Consumer<Throwable> failed) {
        return new CompletionHandler<Void, AsynchronousSocketChannel>() {
            @Override
            public void completed(Void result, AsynchronousSocketChannel attachment) {
                completed.accept(attachment);
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                failed.accept(exc);
            }
        };
    }

    static CompletionHandler<Integer, AsynchronousSocketChannel> onReadWrite(Consumer<Integer> completed, Consumer<Throwable> failed) {
        return new CompletionHandler<Integer, AsynchronousSocketChannel>() {
            @Override
            public void completed(Integer result, AsynchronousSocketChannel attachment) {
                completed.accept(result);
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                failed.accept(exc);
            }
        };

    }

    static CompletionHandler<AsynchronousSocketChannel, Object> onAccept(Consumer<AsynchronousSocketChannel> completed, Consumer<Throwable> failed) {
        return new CompletionHandler<AsynchronousSocketChannel, Object>() {
            @Override
            public void completed(AsynchronousSocketChannel result, Object attachment) {
                completed.accept(result);
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                failed.accept(exc);
            }
        };
    }


}
