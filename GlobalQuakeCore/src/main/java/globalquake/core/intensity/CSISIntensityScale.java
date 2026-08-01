package globalquake.core.intensity;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * CSIS 中国地震烈度表（GB/T 17742-2020）仪器测定烈度。
 *
 * 依据标准表1「各烈度对应仪器测定参数区间」，取各级 PGA 区间下界（gal）作为 Level 阈值；
 * Ⅰ级取 0.5 gal（与项目有感阈值 computePGA=0.5 gal 衔接，小于该值视为无感不显示级别）。
 */
@SuppressWarnings("unused")
public class CSISIntensityScale implements IntensityScale {

	public static final Level I;
	public static final Level II;
	public static final Level III;
	public static final Level IV;
	public static final Level V;
	public static final Level VI;
	public static final Level VII;
	public static final Level VIII;
	public static final Level IX;
	public static final Level X;
	public static final Level XI;
	public static final Level XII;
	private static final List<Level> levels = new ArrayList<>();

	static {
		levels.add(I = new Level("I", 0.5, new Color(170, 170, 170)));     // <2.57
		levels.add(II = new Level("II", 2.58, new Color(200, 190, 240)));  // 2.58~5.28
		levels.add(III = new Level("III", 5.29, new Color(132, 162, 232))); // 5.29~10.8
		levels.add(IV = new Level("IV", 10.9, new Color(130, 214, 255)));  // 10.9~22.2
		levels.add(V = new Level("V", 22.3, new Color(85, 242, 15)));      // 22.3~45.6
		levels.add(VI = new Level("VI", 45.7, new Color(255, 255, 0)));    // 45.7~93.6
		levels.add(VII = new Level("VII", 93.7, new Color(255, 200, 0)));  // 93.7~194
		levels.add(VIII = new Level("VIII", 195, new Color(255, 120, 0))); // 195~401
		levels.add(IX = new Level("IX", 402, new Color(255, 0, 0)));       // 402~830
		levels.add(X = new Level("X", 831, new Color(190, 0, 0)));         // 831~1720
		levels.add(XI = new Level("XI", 1730, new Color(130, 0, 0)));      // 1730~3550
		levels.add(XII = new Level("XII", 3550, new Color(65, 0, 0)));     // >3550
	}

	@Override
	public List<Level> getLevels() {
		return levels;
	}

	@Override
	public String getNameShort() {
		return "CSIS";
	}

	@Override
	public String getNameLong() {
		return "The Chinese seismic intensity scale (GB/T 17742-2020)";
	}

	@Override
	public double getDarkeningFactor() {
		return 0.62;
	}

	@Override
	public String toString() {
		return getNameLong();
	}

}
