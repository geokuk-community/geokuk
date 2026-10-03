package cz.geokuk.plugins.kesoid.mvc;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.*;

import org.junit.Test;

public class BlokovaneZdrojeTest {

	@Test
	public void zdrojeVeSlozceProgramuPrezijiPresun() {
		final File stary = new File("/tmp/stary/GeoKuk").getAbsoluteFile();
		final File novy = new File("/tmp/novy/GeoKuk").getAbsoluteFile();
		final File vSlozce = new File(stary, "data/kese/moje.gpx");
		final File venku = new File("/tmp/geoget/data.db3").getAbsoluteFile();

		final Collection<File> ulozene = KesoidModel.relativneKeKorenu(Arrays.asList(vSlozce, venku), stary);
		assertEquals(new HashSet<>(Arrays.asList(new File("data/kese/moje.gpx"), venku)), new HashSet<>(ulozene));

		assertEquals(new HashSet<>(Arrays.asList(new File(novy, "data/kese/moje.gpx"), venku)), KesoidModel.absolutne(ulozene, novy));
	}

	@Test
	public void staraAbsolutniCestaZustane() {
		final File koren = new File("/tmp/GeoKuk").getAbsoluteFile();
		final File stara = new File("/tmp/jinde/GeoKuk/data/kese/a.gpx").getAbsoluteFile();
		assertEquals(Collections.singleton(stara), KesoidModel.absolutne(Collections.singleton(stara), koren));
	}

	@Test
	public void podobnyNazevSlozkyNeniUvnitr() {
		final File koren = new File("/tmp/GeoKuk").getAbsoluteFile();
		final File vedle = new File("/tmp/GeoKuk2/a.gpx").getAbsoluteFile();
		assertEquals(Collections.singletonList(vedle), KesoidModel.relativneKeKorenu(Collections.singleton(vedle), koren));
	}
}
