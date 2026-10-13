package cz.geokuk.plugins.mapy.kachle.data;

import java.time.Year;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;

/** Topografické podklady Německa, Rakouska a Švýcarska: adresy dlaždic, měřítka, atribuce a hromadné stahování. */
public class EKaTypeTopoTest {

	private static String url(final EKaType typ, final double lat, final double lon, final int z) throws Exception {
		return new Ka(KaLoc.ofJZ(new Wgs(lat, lon).toMou(), z), typ).getUrl().toExternalForm();
	}

	/** Berlín z14: x 8800, y 5373; Vídeň z14: x 8937, y 5681; Bern z14: x 8530, y 5766. */
	@Test
	public void adresyDlazdic() throws Exception {
		Assert.assertEquals("https://sgx.geodatenzentrum.de/wmts_topplus_open/tile/1.0.0/web/default/WEBMERCATOR/14/5373/8800.png", url(EKaType.DE_TOPPLUS, 52.5163, 13.3777, 14));
		Assert.assertEquals("https://maps.wien.gv.at/basemap/geolandbasemap/normal/google3857/14/5681/8937.png", url(EKaType.AT_BASEMAP, 48.2082, 16.3738, 14));
		Assert.assertEquals("x před y", "https://wmts.geo.admin.ch/1.0.0/ch.swisstopo.pixelkarte-farbe/default/current/3857/14/8530/5766.jpeg", url(EKaType.CH_SWISSTOPO, 46.9480, 7.4474, 14));
	}

	@Test
	public void meritkaAtribuceUzemi() {
		Assert.assertEquals(18, EKaType.DE_TOPPLUS.getMaxMoumer());
		Assert.assertEquals(19, EKaType.AT_BASEMAP.getMaxMoumer());
		Assert.assertEquals(18, EKaType.CH_SWISSTOPO.getMaxMoumer());
		Assert.assertEquals("© BKG (" + Year.now() + ") dl-de/by-2-0, Datenquellen: https://sgx.geodatenzentrum.de/web_public/gdz/datenquellen/datenquellen_topplusopen.html", EKaType.DE_TOPPLUS.getAtribuce());
		Assert.assertEquals("Data source: basemap.at", EKaType.AT_BASEMAP.getAtribuce());
		Assert.assertEquals("© swisstopo", EKaType.CH_SWISSTOPO.getAtribuce());
		final KaLoc praha = KaLoc.ofJZ(new Wgs(50.0875, 14.4208).toMou(), 14);
		for (final EKaType t : new EKaType[] { EKaType.DE_TOPPLUS, EKaType.AT_BASEMAP, EKaType.CH_SWISSTOPO }) {
			Assert.assertTrue(t.isOmezeneUzemi());
			Assert.assertTrue("404 = dlaždice mimo území", t.jeMimoUzemi(404, praha));
			Assert.assertFalse(t.jeMimoUzemi(500, praha));
			Assert.assertSame(t, EKaType.podleJmena(t.name()));
			Assert.assertNull(t.getKeyStroke());
			Assert.assertTrue(EKaType.vestavene().contains(t));
		}
	}

	@Test
	public void hromadneStahovani() {
		Assert.assertFalse("BKG chce u offline uložení datovanou atribuci", EKaType.DE_TOPPLUS.isHromadneStahovaniPovoleno());
		Assert.assertTrue(EKaType.AT_BASEMAP.isHromadneStahovaniPovoleno());
		Assert.assertFalse("swisstopo hromadné stahování nechce", EKaType.CH_SWISSTOPO.isHromadneStahovaniPovoleno());
		Assert.assertTrue(EKaType.jeServerBezHromadnehoStahovani("wmts.geo.admin.ch"));
		Assert.assertTrue(EKaType.jeServerBezHromadnehoStahovani("sgx.geodatenzentrum.de"));
	}
}
