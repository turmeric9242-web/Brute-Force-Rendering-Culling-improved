package misanthropy.brute_force_culling_revived.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.function.Consumer;

public class LifeTimer<T> {
    private final Object2IntOpenHashMap<T> usageTick = new Object2IntOpenHashMap<>();

    public void tick(int clientTick, int count) {
        ObjectIterator<Object2IntMap.Entry<T>> iterator = usageTick.object2IntEntrySet().fastIterator();
        while (iterator.hasNext()) {
            if (clientTick - iterator.next().getIntValue() > count) {
                iterator.remove();
            }
        }
    }

    public void updateUsageTick(T hash, int tick) {
        usageTick.put(hash, tick);
    }

    public void refreshIfPresent(T hash, int tick) {
        usageTick.replace(hash, tick);
    }

    public boolean contains(T hash) {
        return usageTick.containsKey(hash);
    }

    public void clear() {
        usageTick.clear();
    }

    public int size() {
        return usageTick.size();
    }

    public void foreach(Consumer<T> consumer) {
        for (T key : usageTick.keySet()) {
            consumer.accept(key);
        }
    }
}
