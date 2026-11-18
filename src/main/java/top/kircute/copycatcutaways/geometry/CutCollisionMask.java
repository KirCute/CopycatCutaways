package top.kircute.copycatcutaways.geometry;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Immutable 8x8x8 collision occupancy result.
 *
 * <p>Bits use {@code (x * 8 + y) * 8 + z}, so increasing x changes one whole
 * 64-bit word, increasing y changes an 8-bit layer inside a word, and increasing
 * z changes a column bit. This layout keeps the shape lookup and axis bounds
 * cheap after loading the mask.</p>
 */
public final class CutCollisionMask {

    public static final int VERSION = 1;

    public static final int SEGMENTS = 8;

    public static final int WORD_COUNT = 8;

    private static final double EPSILON = 1.0E-7;

    private final long[] words;

    private CutCollisionMask(long[] words) {
        this.words = words.clone();
    }

    public static CutCollisionMask fromPlane(CutPlane plane) {
        long[] words = new long[WORD_COUNT];
        boolean addedAny = false;
        double fallbackBest = Double.NEGATIVE_INFINITY;
        int fallbackIndex = -1;
        double cellSize = 1.0 / SEGMENTS;

        for (int x = 0; x < SEGMENTS; x++) {
            double centerX = (x + 0.5) * cellSize;
            for (int y = 0; y < SEGMENTS; y++) {
                double centerY = (y + 0.5) * cellSize;
                for (int z = 0; z < SEGMENTS; z++) {
                    double centerZ = (z + 0.5) * cellSize;
                    double signed = plane.signedDistance(new Vec3(centerX, centerY, centerZ));
                    int index = index(x, y, z);

                    if (signed > fallbackBest) {
                        fallbackBest = signed;
                        fallbackIndex = index;
                    }

                    if (signed >= -EPSILON) {
                        set(words, index);
                        addedAny = true;
                    }
                }
            }
        }

        if (!addedAny && fallbackIndex >= 0) {
            set(words, fallbackIndex);
        }

        return new CutCollisionMask(words);
    }

    @Nullable
    public static CutCollisionMask fromPacked(long[] words) {
        // A valid collision plane never produces an all-empty saved mask; this
        // indicates truncated or corrupted NBT and should trigger recomputation.
        if (words.length != WORD_COUNT || isEmpty(words)) {
            return null;
        }
        return new CutCollisionMask(words);
    }

    public long[] copyWords() {
        return words.clone();
    }

    public VoxelShape toVoxelShape() {
        if (isEmpty()) {
            return Shapes.empty();
        }
        if (isFull()) {
            return Shapes.block();
        }
        return new MergedBoxVoxelShape(new LongArrayDiscreteVoxelShape(words));
    }

    public boolean isEmpty() {
        return isEmpty(words);
    }

    public boolean isFull() {
        for (long word : words) {
            if (word != -1L) {
                return false;
            }
        }
        return true;
    }

    public boolean get(int x, int y, int z) {
        int index = index(x, y, z);
        return (words[index >>> 6] & (1L << (index & 63))) != 0;
    }

    private static boolean isEmpty(long[] words) {
        for (long word : words) {
            if (word != 0L) {
                return false;
            }
        }
        return true;
    }

    private static int index(int x, int y, int z) {
        return (x * SEGMENTS + y) * SEGMENTS + z;
    }

    private static void set(long[] words, int index) {
        words[index >>> 6] |= 1L << (index & 63);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof CutCollisionMask other
                && Arrays.equals(words, other.words);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(words);
    }

    private static final class LongArrayDiscreteVoxelShape extends DiscreteVoxelShape {

        private static final long Y_LAYER_MASK = 0xFFL;

        private static final long Z_COLUMN_MASK = 0x0101010101010101L;

        private final long[] words;

        private LongArrayDiscreteVoxelShape(long[] words) {
            super(SEGMENTS, SEGMENTS, SEGMENTS);
            this.words = words.clone();
        }

        @Override
        public boolean isFull(int x, int y, int z) {
            int index = index(x, y, z);
            return (words[index >>> 6] & (1L << (index & 63))) != 0;
        }

        @Override
        public void fill(int x, int y, int z) {
            int index = index(x, y, z);
            words[index >>> 6] |= 1L << (index & 63);
        }

        @Override
        public boolean isEmpty() {
            for (long word : words) {
                if (word != 0L) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int firstFull(Direction.Axis axis) {
            return switch (axis) {
                case X -> {
                    for (int x = 0; x < SEGMENTS; x++) {
                        if (words[x] != 0L) {
                            yield x;
                        }
                    }
                    yield SEGMENTS;
                }
                case Y -> {
                    for (int y = 0; y < SEGMENTS; y++) {
                        long mask = Y_LAYER_MASK << (y * Byte.SIZE);
                        for (long word : words) {
                            if ((word & mask) != 0L) {
                                yield y;
                            }
                        }
                    }
                    yield SEGMENTS;
                }
                case Z -> {
                    for (int z = 0; z < SEGMENTS; z++) {
                        long mask = Z_COLUMN_MASK << z;
                        for (long word : words) {
                            if ((word & mask) != 0L) {
                                yield z;
                            }
                        }
                    }
                    yield SEGMENTS;
                }
            };
        }

        @Override
        public int lastFull(Direction.Axis axis) {
            return switch (axis) {
                case X -> {
                    for (int x = SEGMENTS - 1; x >= 0; x--) {
                        if (words[x] != 0L) {
                            yield x + 1;
                        }
                    }
                    yield 0;
                }
                case Y -> {
                    for (int y = SEGMENTS - 1; y >= 0; y--) {
                        long mask = Y_LAYER_MASK << (y * Byte.SIZE);
                        for (long word : words) {
                            if ((word & mask) != 0L) {
                                yield y + 1;
                            }
                        }
                    }
                    yield 0;
                }
                case Z -> {
                    for (int z = SEGMENTS - 1; z >= 0; z--) {
                        long mask = Z_COLUMN_MASK << z;
                        for (long word : words) {
                            if ((word & mask) != 0L) {
                                yield z + 1;
                            }
                        }
                    }
                    yield 0;
                }
            };
        }
    }
}
