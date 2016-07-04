package bg.royal.orm.collections;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Ivan Yonkov
 */
public class LazyResultSet<T> implements Iterable<T> {

    private Supplier<T> supplier;

    private T last;

    private List<T> result;

    private int lastIndex = -1;
    private int count = 0;
    private long iterations = -1L;

    public LazyResultSet(Supplier<T> supplier) {
        this.supplier = supplier;
        this.result = new ArrayList<>();
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                iterations++;
                last = supplier.get();
                if (last != null) count++;
                T lastLocal = last;
                if (last == null) {
                    if (lastIndex >= count - 1) {
                        lastIndex = -1;
                    }
                    last = result.get(++lastIndex);
                    if (iterations < count) {
                        lastLocal = last;
                    } else {
                        iterations = -1;
                    }

                }
                return lastLocal != null;
            }

            @Override
            public T next() {
                if (lastIndex == -1)
                    result.add(last);
                return last;
            }
        };
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        while (this.iterator().hasNext()) {
            T entity = this.iterator().next();
            action.accept(entity);
        }
    }

    @Override
    public Spliterator<T> spliterator() {
        return null;
    }


}
