package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank;

public enum TankType {
	EMPTY, LAVA, WATER, MILK, MUSHROOM_STEW, RABBIT_STEW, BEETROOT_SOUP, HONEY, DRAGON_BREATH, EXPERIENCE;

	public static TankType getTankType(String paramString) {
		for (TankType tankType : values()) {
			if (tankType.toString().replace("_", "").equalsIgnoreCase(paramString.toLowerCase().replace("_", "")))
				return tankType;
		}
		return EMPTY;
	}
}
