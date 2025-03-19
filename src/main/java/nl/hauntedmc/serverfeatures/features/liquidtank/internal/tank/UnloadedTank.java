package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank;

public class UnloadedTank {
	String world;

	int x;

	int y;

	int z;

	int quantity;

	TankType tp;

	public UnloadedTank(String paramString, int paramInt1, int paramInt2, int paramInt3, TankType paramTankType, int paramInt4) {
		this.world = paramString;
		this.x = paramInt1;
		this.y = paramInt2;
		this.z = paramInt3;
		this.quantity = paramInt4;
		this.tp = paramTankType;
	}

	public String getWorld() {
		return this.world;
	}

	public int getX() {
		return this.x;
	}

	public int getY() {
		return this.y;
	}

	public int getZ() {
		return this.z;
	}

	public TankType getType() {
		return this.tp;
	}

	public int getQuantity() {
		return this.quantity;
	}
}
