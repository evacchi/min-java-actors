package io.github.evacchi.jquirky;

import java.util.concurrent.Callable;
import java.util.function.*;

public interface JQuirky {

    static <T,R> Function<T,R> $(Function<T,R> f) { return f; }
    static <T,U,R> BiFunction<T,U,R> $(BiFunction<T,U,R> f) { return f; }
    static <T> Consumer<T> $(Consumer<T> f) { return f; }
    static <T> UnaryOperator<T> $(UnaryOperator<T> f) { return f; }
    static <T> BinaryOperator<T> $(BinaryOperator<T> f) { return f; }
    static <T,U> BiPredicate<T,U> $(BiPredicate<T,U> f) { return f; }
    static <T> Predicate<T> $(Predicate<T> f) { return f; }
    static <T> Supplier<T> $(Supplier<T> f) { return f; }
    static <T> Callable<T> $(Callable<T> f) { return f; }

    static Runnable $(Runnable f) { return f; }

    public static void main(String[] args) {



    }

}
