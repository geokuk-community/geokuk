package cz.geokuk.core.coordinates;

/**
 * Rozdíl dvou mouřadnic nebo rozměr v mouřadnicích. Na rozdíl od polohy je
 * v longu, protože rozdíl může být větší než celý svět (při malém měřítku,
 * kdy se svět v okně opakuje).
 */
public class Moud {

	public long dxx;
	public long dyy;

	public Moud() {}

	public Moud(final long dxx, final long dyy) {
		this.dxx = dxx;
		this.dyy = dyy;
	}

	public Moud(final Moud mou) {
		dxx = mou.dxx;
		dyy = mou.dyy;
	}

	public Moud add(final long dxx, final long dyy) {
		return new Moud(this.dxx + dxx, this.dyy + dyy);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof Moud)) {
			return false;
		}
		final Moud m = (Moud) obj;
		return dxx == m.dxx && dyy == m.dyy;
	}

	public long getKvadratVzdalenosti() {
		return dxx * dxx + dyy * dyy;
	}

	@Override
	public int hashCode() {
		return (int) (dxx ^ dyy);
	}

	public boolean isAnyRozmerEmpty() {
		return dxx <= 0 || dyy <= 0;
	}

	public Moud sub(final long dxx, final long dyy) {
		return new Moud(this.dxx - dxx, this.dyy - dyy);
	}

	@Override
	public String toString() {
		return "[" + Long.toHexString(dxx) + "," + Long.toHexString(dyy) + "]";
	}
}
