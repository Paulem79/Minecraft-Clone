package ovh.paulem.mc.math;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ArraysUtils {
    public static <T> T getRandom(T[] array) {
        int rnd = FastRandom.INSTANCE.nextInt(array.length);
        return array[rnd];
    }

    public static <T> T getRandom(List<T> list) {
        int rnd = FastRandom.INSTANCE.nextInt(list.size());
        return list.get(rnd);
    }

    public static <T> T getRandom(Collection<T> collection) {
        return getRandom(new ArrayList<>(collection));
    }

    public static <T> List<T> difference(List<T> a, List<T> b) {
        List<T> result = new ArrayList<>(a);
        result.removeAll(b);
        return result;
    }

    public static <T> T[] excludeElements(T[] array, Collection<T> toExclude) {
        List<T> result = new ArrayList<>();
        for (T element : array) {
            if (!toExclude.contains(element)) {
                result.add(element);
            }
        }
        @SuppressWarnings("unchecked")
        T[] excluded = (T[]) java.lang.reflect.Array.newInstance(array.getClass().getComponentType(), result.size());
        return result.toArray(excluded);
    }

    public static <T> T[] invertArray(T[] array) {
        for (int i = 0, j = array.length - 1; i < j; i++, j--) {
            T temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }

        return array;
    }
}
