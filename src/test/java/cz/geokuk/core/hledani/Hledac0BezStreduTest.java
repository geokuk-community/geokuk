package cz.geokuk.core.hledani;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;

/** Bez středu hledání se vzdálenost ani azimut nepočítají. */
public class Hledac0BezStreduTest {

	@Test
	public void bezStreduNicNepocita() {
		final Nalezenec0 nal = new Nalezenec0() {
			@Override
			public Wgs getWgs() {
				return new Wgs(50, 14);
			}
		};
		new Hledac0<Nalezenec0>() {
			@Override
			public List<Nalezenec0> hledej(final HledaciPodminka0 podm) {
				return Collections.emptyList();
			}
		}.dopicitejVzdalenostAAzimut(nal, null);
		Assert.assertEquals(0, nal.getVzdalenost(), 0);
	}
}
