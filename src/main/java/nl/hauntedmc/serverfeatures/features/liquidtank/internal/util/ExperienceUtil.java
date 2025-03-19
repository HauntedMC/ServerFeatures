package nl.hauntedmc.serverfeatures.features.liquidtank.internal.util;

import org.bukkit.entity.Player;

public class ExperienceUtil {
	public static void addExp(Player paramPlayer, int paramInt) {
		paramPlayer.giveExp(paramInt);
		int i = getLevel(totalExp(paramPlayer));
		if (i > paramPlayer.getLevel()) {
			paramPlayer.setExp(0.0F);
			paramPlayer.setLevel(i);
		}
	}

	public static void removeExp(Player paramPlayer, int paramInt) {
		int i = totalExp(paramPlayer);
		paramPlayer.setExp(0.0F);
		paramPlayer.setLevel(0);
		addExp(paramPlayer, i - paramInt);
		paramPlayer.setTotalExperience(i - paramInt);
	}

	public static int totalExp(Player paramPlayer) {
		double d = Double.parseDouble(Float.toString(paramPlayer.getExp()));
		int i = (int) Math.round(
				lvlToExp(paramPlayer.getLevel()) + expToLvlUp(paramPlayer.getLevel()) * d);
		if (i < paramPlayer.getTotalExperience())
			paramPlayer.setTotalExperience(i);
		return i;
	}

	public static int getLevel(int paramInt) {
		byte b1 = 0;
		for (byte b2 = 1; b2 <= 30; b2++) {
			int i;
			if (b2 > 16) {
				i = (int) (2.5D * b2 * b2 - 40.5D * b2 + 360.0D);
			} else {
				i = b2 * b2 + 6 * b2;
			}
			if (paramInt >= i) {
				b1 = b2;
			} else {
				b2 = 30;
			}
		}
		return b1;
	}

	public static int lvlToExp(double paramDouble) {
		int i = 0;
		if (paramDouble <= 16.0D && paramDouble >= 0.0D)
			i = (int) Math.round(paramDouble * paramDouble + 6.0D * paramDouble);
		if (paramDouble <= 30.0D && paramDouble >= 17.0D)
			i = (int) Math.round(2.5D * paramDouble * paramDouble - 40.5D * paramDouble + 360.0D);
		if (paramDouble > 30.0D)
			i = (int) Math.round(4.5D * paramDouble * paramDouble - 162.5D * paramDouble + 2220.0D);
		return i;
	}

	public static int expToLvlUp(int paramInt) {
		int i = 0;
		if (paramInt <= 16 && paramInt >= 0)
			i = 2 * paramInt + 7;
		if (paramInt <= 31 && paramInt >= 17)
			i = 5 * paramInt - 38;
		if (paramInt > 31)
			i = 9 * paramInt - 158;
		return i;
	}
}
