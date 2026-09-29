package cz.geokuk.core.coordinates;

import cz.geokuk.util.index2d.BoundingRect;

public class MouRect {

	private int xx1;
	private int yy1;
	private int xx2;
	private int yy2;

	public Mou sstre;

	public MouRect() {}

	public MouRect(final Mou mou) {
		add(mou);
	}

	public MouRect(final Mou roh1, final Mou roh2) {
		add(roh1);
		add(roh2);
		sstre = new Mou(stred(roh1.xx, roh2.xx), stred(roh1.yy, roh2.yy));
		// assert roh1.xx == xx1;
		// assert roh2.xx == xx2;
		// assert roh1.yy == yy1 : roh1.yy + " " + yy1;
		// assert roh2.yy == yy2;
	}

	public void add(final Mou mou) {
		if (isEmpty()) { // tak jsou všechny nulové
			xx1 = mou.xx;
			xx2 = mou.xx;
			yy1 = mou.yy;
			yy2 = mou.yy;
		} else {
			if (mou.xx < xx1) {
				xx1 = mou.xx;
			}
			if (mou.xx > xx2) {
				xx2 = mou.xx;
			}
			if (mou.yy < yy1) {
				yy1 = mou.yy;
			}
			if (mou.yy > yy2) {
				yy2 = mou.yy;
			}
		}

	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final MouRect other = (MouRect) obj;
		if (xx1 != other.xx1) {
			return false;
		}
		if (xx2 != other.xx2) {
			return false;
		}
		if (yy1 != other.yy1) {
			return false;
		}
		if (yy2 != other.yy2) {
			return false;
		}
		return true;
	}

	public BoundingRect getBoundingRect() {
		return new BoundingRect(xx1, yy1, xx2, yy2);
	}

	public Mou getJv() {
		return new Mou(xx2, yy1);
	}

	public Mou getJz() {
		return new Mou(xx1, yy1);
	}

	public long getMouHeight() {
		return (long) yy2 - yy1;
	}

	public long getMouWidth() {
		return (long) xx2 - xx1;
	}

	public Mou getStred() {
		return new Mou(stred(xx1, xx2), stred(yy1, yy2));
	}

	public Mou getSv() {
		return new Mou(xx2, yy2);
	}

	public Mou getSz() {
		return new Mou(xx1, yy2);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + xx1;
		result = prime * result + xx2;
		result = prime * result + yy1;
		result = prime * result + yy2;
		return result;
	}

	public boolean isEmpty() {
		return xx1 == 0 && xx2 == 0 & yy1 == 0 & yy2 == 0;
	}

	public boolean isHorizontalLine() {
		return yy1 == yy2 && xx1 < xx2;
	}

	public boolean isLine() {
		return isVerticalLine() || isHorizontalLine();
	}

	public boolean isPoint() {
		return !isEmpty() && xx1 == xx2 && yy1 == yy2;
	}

	public boolean isRecangle() {
		return xx1 < xx2 && yy1 < yy2;
	}

	public boolean isVerticalLine() {
		return xx1 == xx2 && yy1 < yy2;
	}

	/**
	 * Zvětší nebo zmenší velikost v daném poměru
	 */
	public void resize(final double pomer) {
		final long dx = (long) ((getMouWidth() * pomer - getMouWidth()) / 2);
		final long dy = (long) ((getMouHeight() * pomer - getMouHeight()) / 2);
		xx1 = orizni((long) xx1 - dx);
		xx2 = orizni((long) xx2 + dx);
		yy1 = orizni((long) yy1 - dy);
		yy2 = orizni((long) yy2 + dy);
	}

	/** Střed dvou souřadnic; součet dvou velkých mouřadnic by v intu přetekl. */
	private static int stred(final int souradnice1, final int souradnice2) {
		return (int) (((long) souradnice1 + souradnice2) / 2);
	}

	/**
	 * Zvětšený obdélník nesmí přesáhnout okraj světa; přetočil by se a vyšel by
	 * z něj výřez, ve kterém skoro nic neleží.
	 */
	private static int orizni(final long souradnice) {
		return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, souradnice));
	}

	@Override
	public String toString() {
		return "MouRect [xx1=" + xx1 + ", xx2=" + xx2 + ", yy1=" + yy1 + ", yy2=" + yy2 + "]";
	}

}
