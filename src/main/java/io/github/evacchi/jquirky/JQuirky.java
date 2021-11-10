package io.github.evacchi.jquirky;

import java.util.concurrent.Callable;
import java.util.function.*;

public interface JQuirky {


//    static <T,R> Function<T,R> $(Function<T,R> f) { return f; }
//    static <T,U,R> BiFunction<T,U,R> $(BiFunction<T,U,R> f) { return f; }
//    static <T> Consumer<T> $(Consumer<T> f) { return f; }
//    static <T> UnaryOperator<T> $(UnaryOperator<T> f) { return f; }
//    static <T> BinaryOperator<T> $(BinaryOperator<T> f) { return f; }
//    static <T,U> BiPredicate<T,U> $(BiPredicate<T,U> f) { return f; }
//    static <T> Predicate<T> $(Predicate<T> f) { return f; }
//    static <T> Supplier<T> $(Supplier<T> f) { return f; }
//    static <T> Callable<T> $(Callable<T> f) { return f; }

    static <T, U> BiConsumer<T, U> $(BiConsumer<T, U> f) { return f; }
    static <T, U, R> BiFunction<T, U, R> $(BiFunction<T, U, R> f) { return f; }
    static <T, U> BiPredicate<T, U> $(BiPredicate<T, U> f) { return f; }
    static <T> BinaryOperator<T> $(BinaryOperator<T> f) { return f; }
    static BooleanSupplier $(BooleanSupplier f) { return f; }
    static <T> Consumer<T> $(Consumer<T> f) { return f; }
    static DoubleBinaryOperator $(DoubleBinaryOperator f) { return f; }
    static DoubleConsumer $(DoubleConsumer f) { return f; }
    static <R> DoubleFunction<R> $(DoubleFunction<R> f) { return f; }
    static DoublePredicate $(DoublePredicate f) { return f; }
    static DoubleSupplier $(DoubleSupplier f) { return f; }
    static DoubleToIntFunction $(DoubleToIntFunction f) { return f; }
    static DoubleToLongFunction $(DoubleToLongFunction f) { return f; }
    static DoubleUnaryOperator $(DoubleUnaryOperator f) { return f; }
    static <T, R> Function<T, R> $(Function<T, R> f) { return f; }
    static IntBinaryOperator $(IntBinaryOperator f) { return f; }
    static IntConsumer $(IntConsumer f) { return f; }
    static <R> IntFunction<R> $(IntFunction<R> f) { return f; }
    static IntPredicate $(IntPredicate f) { return f; }
    static IntSupplier $(IntSupplier f) { return f; }
    static IntToDoubleFunction $(IntToDoubleFunction f) { return f; }
    static IntToLongFunction $(IntToLongFunction f) { return f; }
    static IntUnaryOperator $(IntUnaryOperator f) { return f; }
    static LongBinaryOperator $(LongBinaryOperator f) { return f; }
    static LongConsumer $(LongConsumer f) { return f; }
    static <R> LongFunction<R> $(LongFunction<R> f) { return f; }
    static LongPredicate $(LongPredicate f) { return f; }
    static LongSupplier $(LongSupplier f) { return f; }
    static LongToDoubleFunction $(LongToDoubleFunction f) { return f; }
    static LongToIntFunction $(LongToIntFunction f) { return f; }
    static LongUnaryOperator $(LongUnaryOperator f) { return f; }
    static <T> ObjDoubleConsumer<T> $(ObjDoubleConsumer<T> f) { return f; }
    static <T> ObjIntConsumer<T> $(ObjIntConsumer<T> f) { return f; }
    static <T> ObjLongConsumer<T> $(ObjLongConsumer<T> f) { return f; }
    static <T> Predicate<T> $(Predicate<T> f) { return f; }
    static <T> Supplier<T> $(Supplier<T> f) { return f; }
    static <T, U> ToDoubleBiFunction<T, U> $(ToDoubleBiFunction<T, U> f) { return f; }
    static <T> ToDoubleFunction<T> $(ToDoubleFunction<T> f) { return f; }
    static <T, U> ToIntBiFunction<T, U> $(ToIntBiFunction<T, U> f) { return f; }
    static <T> ToIntFunction<T> $(ToIntFunction<T> f) { return f; }
    static <T, U> ToLongBiFunction<T, U> $(ToLongBiFunction<T, U> f) { return f; }
    static <T> ToLongFunction<T> $(ToLongFunction<T> f) { return f; }
    static <T> UnaryOperator<T> $(UnaryOperator<T> f) { return f; }

    static Runnable $(Runnable f) { return f; }

    public static void main(String[] args) {

        var x = $((String y) -> 1);

    }

}
