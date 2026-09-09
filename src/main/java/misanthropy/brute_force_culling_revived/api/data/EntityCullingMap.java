package misanthropy.brute_force_culling_revived.api.data;

import misanthropy.brute_force_culling_revived.api.Config;
import misanthropy.brute_force_culling_revived.api.CullingStateManager;
import misanthropy.brute_force_culling_revived.api.ModLoader;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import misanthropy.brute_force_culling_revived.util.LifeTimer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;
import java.util.function.Consumer;

import static net.minecraftforge.common.extensions.IForgeBlockEntity.INFINITE_EXTENT_AABB;

public class EntityCullingMap extends CullingMap {
    private final EntityMap entityMap = new EntityMap();

    public EntityCullingMap(int width, int height) {
        super(width, height);
    }

    @Override
    protected boolean shouldUpdate() {
        return true;
    }

    @Override
    int configDelayCount() {
        return Config.getDepthUpdateDelay();
    }

    @Override
    int bindFrameBufferId() {
        return CullingStateManager.ENTITY_CULLING_MAP_TARGET.frameBufferId;
    }

    public boolean isObjectVisible(Object o) {
        int idx = entityMap.getIndex(o);
        if (idx == -1) {
            entityMap.addTemp(o, CullingStateManager.clientTickCount);
            return true;
        }

        entityMap.tempObjectTimer.refreshIfPresent(o, CullingStateManager.clientTickCount);

        return isSlotVisible(idx);
    }

    public boolean isObjectVisibleForTick(Object o) {
        int idx = entityMap.getIndex(o);
        return idx == -1 || isSlotVisible(idx);
    }

    private boolean isSlotVisible(int idx) {
        int bufferIdx = 1 + idx * 4;
        return bufferIdx >= cullingBuffer.limit() || (cullingBuffer.get(bufferIdx) & 0xFF) > 0;
    }

    @Override
    public void readData() {
        super.readData();
        entityMap.readUpload();
    }

    public @NotNull EntityMap getEntityTable() {
        return entityMap;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        entityMap.clear();
    }

    public static class EntityMap {
        private final ObjectLinkedOpenHashSet<Object> indexMap = new ObjectLinkedOpenHashSet<>();
        public final LifeTimer<Object> tempObjectTimer = new LifeTimer<>();
        private Object2IntOpenHashMap<Object> uploadEntity = newIndexMap();
        private Object2IntOpenHashMap<Object> readEntity = newIndexMap();
        private final AttributeWriter attributeWriter = new AttributeWriter();

        private static Object2IntOpenHashMap<Object> newIndexMap() {
            Object2IntOpenHashMap<Object> map = new Object2IntOpenHashMap<>();
            map.defaultReturnValue(-1);
            return map;
        }

        public void addObject(Object obj) {
            if (obj instanceof Entity e && !e.isAlive()) return;
            if (obj instanceof BlockEntity be && be.isRemoved()) return;
            indexMap.add(obj);
        }

        public void addTemp(Object obj, int tickCount) {
            tempObjectTimer.updateUsageTick(obj, tickCount);
        }

        public void copyTemp(@NotNull EntityMap other, int tickCount) {
            other.tempObjectTimer.foreach(o -> addTemp(o, tickCount));
            this.uploadEntity.putAll(other.uploadEntity);
            this.readEntity.clear();
            this.readEntity.putAll(other.readEntity);
        }

        public int getIndex(Object obj) {
            return readEntity.getInt(obj);
        }

        public void readUpload() {
            Object2IntOpenHashMap<Object> tempMap = readEntity;
            readEntity = uploadEntity;
            uploadEntity = tempMap;
            uploadEntity.clear();
        }

        public void clearUpload() {
            uploadEntity.clear();
        }

        public void clearIndexMap() {
            indexMap.clear();
        }

        public void tickTemp(int tickCount, int keepAliveTicks) {
            tempObjectTimer.tick(tickCount, keepAliveTicks);
        }

        public void addAllTemp() {
            tempObjectTimer.foreach(this::addObject);
        }

        public void clear() {
            indexMap.clear();
            tempObjectTimer.clear();
            uploadEntity.clear();
            readEntity.clear();
        }

        private static final class AttributeWriter implements Consumer<FloatBuffer> {
            private float index;
            private float sizeXZ;
            private float sizeY;
            private float centerX;
            private float centerY;
            private float centerZ;

            @Override
            public void accept(@NotNull FloatBuffer buffer) {
                buffer.put(index);
                buffer.put(sizeXZ);
                buffer.put(sizeY);
                buffer.put(centerX);
                buffer.put(centerY);
                buffer.put(centerZ);
            }
        }

        public void addEntityAttribute(@NotNull Consumer<Consumer<FloatBuffer>> consumer) {
            clearUpload();
            int stalenessTicks = CullingStateManager.getKeepAliveTicks();
            AttributeWriter writer = attributeWriter;
            int index = 0;
            for (Object o : indexMap) {
                int slot = index++;
                AABB aabb = ModLoader.getObjectAABB(o);
                if (aabb == null || aabb == INFINITE_EXTENT_AABB) continue;

                float inflate = 0.0F;
                if (o instanceof Entity e) {
                    double step = Math.max(Math.abs(e.getX() - e.xOld),
                            Math.max(Math.abs(e.getY() - e.yOld), Math.abs(e.getZ() - e.zOld)));
                    inflate = (float) Math.min(step * stalenessTicks, 3.0);
                }

                writer.index = slot;
                writer.sizeXZ = (float) Math.max(aabb.getXsize(), aabb.getZsize()) + 0.5F + inflate;
                writer.sizeY = (float) aabb.getYsize() + 0.5F + inflate;
                writer.centerX = (float) (aabb.minX + 0.5 * (aabb.maxX - aabb.minX));
                writer.centerY = (float) (aabb.minY + 0.5 * (aabb.maxY - aabb.minY));
                writer.centerZ = (float) (aabb.minZ + 0.5 * (aabb.maxZ - aabb.minZ));
                consumer.accept(writer);

                uploadEntity.put(o, slot);
            }
        }

        public int size() {
            return indexMap.size();
        }
    }
}
