package cz.geokuk.plugins.mapy;

import java.io.File;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.mapy.kachle.data.EKaType;

/** Uložený podklad, i ze starší verze 5.2. */
public class UlozenyPodkladTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void jmenoVestaveneMapy() {
		Assert.assertSame(EKaType.TURIST_M, MapyModel.ulozenyPodklad("TURIST_M"));
		Assert.assertSame(EKaType.OPHOTO_M, MapyModel.ulozenyPodklad("OPHOTO_M"));
		Assert.assertSame(EKaType.CUZK_ORTO, MapyModel.ulozenyPodklad("CUZK_ORTO"));
	}

	@Test
	public void mapaVerze52SObdobou() {
		Assert.assertSame(EKaType.OPEN_STREET, MapyModel.ulozenyPodklad("osm"));
		Assert.assertSame(EKaType.CUZK_ORTO, MapyModel.ulozenyPodklad("cuzksat"));
		Assert.assertSame(EKaType.CUZK_ZTM, MapyModel.ulozenyPodklad("cuzktop"));
	}

	@Test
	public void priStartuMapaVerze52() {
		Assert.assertSame(EKaType.OPEN_STREET, MapyModel.podkladPriStartu("osm", () -> new File(tmp.getRoot(), "neni")));
		Assert.assertSame(EKaType.TURIST_M, MapyModel.podkladPriStartu("TURIST_M", () -> new File(tmp.getRoot(), "neni")));
	}

	@Test
	public void priStartuNeznamaMapaDaVychozi() {
		Assert.assertSame(EKaType.CUZK_ORTO, MapyModel.podkladPriStartu("googlemaps", () -> new File(tmp.getRoot(), "neni")));
		Assert.assertSame(EKaType.CUZK_ORTO, MapyModel.podkladPriStartu(null, () -> new File(tmp.getRoot(), "neni")));
	}

	@Test
	public void neznamaMapaNema() {
		Assert.assertNull(MapyModel.ulozenyPodklad("googlemaps"));
		Assert.assertNull(MapyModel.ulozenyPodklad("bingaerial"));
		Assert.assertNull(MapyModel.ulozenyPodklad("OSM"));
	}
}
