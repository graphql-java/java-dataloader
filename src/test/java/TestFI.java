import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;

public class TestFI {

    interface BL<K, V> {
        List<V> load(List<K> keys);
    }

    interface MapBL<K, V> {
        Map<K, V> load(List<K> keys);
    }

    interface BLwithContext<K, V> {
        List<V> load(List<K> keys, Object context);
    }

    interface MapBLwithContext<K, V> {
        Map<K, V> load(List<K> keys, Object context);
    }


    static class DL<K, V> {
        public DL(BL<K, V> loader) {
        }

        public DL(BLwithContext<K, V> loader) {
        }

        public DL(MapBLwithContext<K, V> loader) {
        }


        public static <K, V> DL<K, V> newDLWithMap(MapBLwithContext<K, V> loader) {
            return null;
        }

        public static <K, V> DL<K, V> newDL(BLwithContext<K, V> loader) {
            return null;
        }
    }


    public static void main(String[] args) {
        DL<Integer, String> dl = new DL<>(keys -> keys.stream()
                .map(Object::toString)
                .collect(Collectors.toList()));

        BLwithContext<Integer, String> integerStringBL = (keys, env) -> keys.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        DL<Integer, String> dl2 = new DL<Integer, String>(integerStringBL);

        MapBLwithContext<Integer, String> mapBLwithContext = (keys, env) -> emptyMap();
        dl = new DL<>(mapBLwithContext);

        DL.newDLWithMap(((keys, context) -> emptyMap()));
    }
}
