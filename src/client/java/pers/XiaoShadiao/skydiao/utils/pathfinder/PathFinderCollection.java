package pers.XiaoShadiao.skydiao.utils.pathfinder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class PathFinderCollection {

    private final Map<Long, PathHub> positionIndex = new HashMap<>();
    private final List<PathHub> elements = Collections.synchronizedList(new ArrayList<>());

    private final List<PathHub> immutableElements = Collections.unmodifiableList(elements);

    private boolean needsSorting = false;

    public List<PathHub> getOriginalList() {
        return immutableElements;
    }

    // 添加元素
    public boolean add(PathHub hub) {
        long hash = getPositionHash(hub.getLoc());
        if (!positionIndex.containsKey(hash)) {
            positionIndex.put(hash, hub);
            elements.add(hub);
            needsSorting = true;
            return true;
        }
        return false;
    }

    public boolean remove(PathHub hub) {
        long hash = getPositionHash(hub.getLoc());
        if (positionIndex.containsKey(hash)) {
            positionIndex.remove(hash);
            elements.remove(hub);
            return true;
        }
        return false;
    }

    // 快速查找
    public PathHub getByPosition(BlockPos pos) {
        return positionIndex.get(getPositionHash(pos));
    }

    public boolean containsPosition(BlockPos pos) {
        return positionIndex.containsKey(getPositionHash(pos));
    }

    // 排序支持
    public void sort(Comparator<PathHub> comparator) {
        if (needsSorting) {
            elements.sort(comparator);
            needsSorting = false;
        }
    }

    public void sortSubList(int fromIndex, int toIndex, Comparator<PathHub> comparator) {
        if (needsSorting) {
            elements.subList(fromIndex, toIndex).sort(comparator);
            needsSorting = false;
        }
    }

    // 列表操作代理
    public int size() { return elements.size(); }
    public boolean isEmpty() { return elements.isEmpty(); }
    public PathHub get(int index) { return elements.get(index); }
    public Iterator<PathHub> iterator() { return elements.iterator(); }

    // 批量操作
    public void addAll(Collection<PathHub> hubs) {
        for (PathHub hub : hubs) {
            add(hub);
        }
    }

    public void addAll(PathFinderCollection hubs) {
        addAll(hubs.getOriginalList());
    }

    public void clear() {
        positionIndex.clear();
        elements.clear();
        needsSorting = false;
    }

    private long getPositionHash(BlockPos pos) {
        return pos.asLong();
    }

    public boolean contains(PathHub hub) {
        return containsPosition(hub.getLoc());
    }

    public Stream<PathHub> stream() {
        return elements.stream();
    }

    public static <T> T runFunctionWithListLock(PathFinderCollection list, Function<PathFinderCollection, T> function) {
        synchronized (list.elements) {
            return function.apply(list);
        }
    }
}