import java.util.ArrayList;
import java.util.List;

public final class SodiumHookSemanticsCheck {

	private static final class World {
		boolean checkCulling;
		boolean shouldCullChunk;
		boolean sectionRenders;
		boolean sectionNull;
		boolean frustumVisible;
		boolean renderingEntities;
		final List<Boolean> counted = new ArrayList<>();

		boolean shouldRenderChunk() {
			if (sectionNull) return false;
			if (!shouldCullChunk) return true;
			return sectionRenders;
		}

		void countChunk(boolean culled) {
			counted.add(culled);
		}

		String trace(Object result) {
			return result + " counted=" + counted;
		}
	}

	private static String occlusionBefore(World world, boolean returnValue) {
		boolean value = returnValue;

		if (!value) return world.trace(value);
		if (world.checkCulling) {
			value = true;
			return world.trace(value);
		}
		if (!world.shouldCullChunk) return world.trace(value);

		boolean culled = !world.shouldRenderChunk();
		world.countChunk(culled);
		if (culled) {
			value = false;
		}

		return world.trace(value);
	}

	private static String occlusionAfter(World world, boolean visible) {
		if (!visible) return world.trace(false);
		if (world.checkCulling) return world.trace(true);
		if (!world.shouldCullChunk) return world.trace(true);

		boolean culled = !world.shouldRenderChunk();
		world.countChunk(culled);

		return world.trace(!culled);
	}

	private static String managerBefore(World world, boolean returnValue) {
		boolean value = returnValue;

		if (!world.shouldCullChunk) return world.trace(value);

		if (!world.shouldRenderChunk()) {
			value = false;
			return world.trace(value);
		}

		value = world.frustumVisible;
		return world.trace(value);
	}

	private static String managerAfter(World world, boolean visible) {
		if (!world.shouldCullChunk) return world.trace(visible);

		if (!world.shouldRenderChunk()) {
			return world.trace(false);
		}

		return world.trace(world.frustumVisible);
	}

	private static String regionBefore(World world, String arraySlot) {
		if (world.renderingEntities && arraySlot == null) {
			return "placeholder";
		}

		return String.valueOf(arraySlot);
	}

	private static String regionAfter(World world, String arraySlot) {
		String section = arraySlot;

		if (world.renderingEntities && section == null) {
			return "placeholder";
		}

		return String.valueOf(section);
	}

	private static World world(int bits) {
		World world = new World();
		world.checkCulling = (bits & 1) != 0;
		world.shouldCullChunk = (bits & 2) != 0;
		world.sectionRenders = (bits & 4) != 0;
		world.sectionNull = (bits & 8) != 0;
		world.frustumVisible = (bits & 16) != 0;
		world.renderingEntities = (bits & 32) != 0;
		return world;
	}

	public static void main(String[] args) {
		int cases = 0;

		for (int bits = 0; bits < 64; bits++) {
			for (int input = 0; input < 2; input++) {
				boolean value = input != 0;

				String a = occlusionBefore(world(bits), value);
				String b = occlusionAfter(world(bits), value);
				if (!a.equals(b)) {
					throw new AssertionError("OcclusionCuller.isSectionVisible bits=" + bits
						+ " in=" + value + ": was " + a + ", now " + b);
				}

				a = managerBefore(world(bits), value);
				b = managerAfter(world(bits), value);
				if (!a.equals(b)) {
					throw new AssertionError("RenderSectionManager.isSectionVisible bits=" + bits
						+ " in=" + value + ": was " + a + ", now " + b);
				}

				String slot = value ? "section" : null;
				a = regionBefore(world(bits), slot);
				b = regionAfter(world(bits), slot);
				if (!a.equals(b)) {
					throw new AssertionError("RenderRegion.getSection bits=" + bits
						+ " slot=" + slot + ": was " + a + ", now " + b);
				}

				cases++;
			}
		}

		if (cases != 128) {
			throw new AssertionError("expected 128 cases, ran " + cases);
		}

		System.out.println("SodiumHookSemanticsCheck: ok");
	}
}
