package io.github.evacchi.asyncchat;

import java.nio.channels.CompletionHandler;
import java.util.function.BiConsumer;

public interface Channels {
    static <A,B> CompletionHandler<A, B> handler(BiConsumer<A,B> completed, BiConsumer<Throwable,B> failed) {
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
