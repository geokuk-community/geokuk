package cz.geokuk.core.program;

import java.io.File;
import java.util.*;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.render.*;
import cz.geokuk.plugins.kesoid.mvc.*;
import cz.geokuk.util.file.Filex;

public class JPrehledSouboruTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** „Načítat až po vybrání“ a nová složka GSAK v jednom uložení: model musí dostat volbu dřív než složku. */
	@Test
	public void volbaGsakSeUloziPredSlozkami() throws Exception {
		final JPrehledSouboru panel = new JPrehledSouboru(null);
		final List<String> volani = new ArrayList<>();
		final KesoidModel kesoidModel = new KesoidModel() {
			@Override
			public void setGsakParametryNacitani(final GsakParametryNacitani g) {
				volani.add("gsak " + g.isNacistVsechnyDatabaze());
			}

			@Override
			public void setUmisteniSouboru(final KesoidUmisteniSouboru u) {
				volani.add("složky");
			}
		};
		final RenderModel renderModel = new RenderModel() {
			@Override
			public void setUmisteniSouboru(final RenderUmisteniSouboru u) {
				volani.add("render");
			}
		};
		panel.inject(kesoidModel);
		panel.inject(renderModel);

		final KesoidUmisteniSouboru u = new KesoidUmisteniSouboru();
		u.setKesDir(slozka("kese"));
		u.setGeogetDataDir(slozka("geoget"));
		u.setGsakDataDir(slozka("gsak"));
		panel.onEvent(new KesoidUmisteniSouboruChangedEvent(u));
		final RenderUmisteniSouboru r = new RenderUmisteniSouboru();
		r.setOziDir(slozka("ozi"));
		r.setKmzDir(slozka("kmz"));
		r.setPictureDir(slozka("obrazky"));
		panel.onEvent(new RenderUmisteniSouboruChangedEvent(r));
		final GsakParametryNacitani g = new GsakParametryNacitani();
		g.setCasNalezu(Collections.emptySet());
		g.setCasNenalezu(Collections.emptySet());
		g.setNacistVsechnyDatabaze(false);
		panel.onEvent(new GsakParametryNacitaniChangedEvent(g));

		panel.uloz();

		Assert.assertEquals(Arrays.asList("gsak false", "složky", "render"), volani);
	}

	private Filex slozka(final String jmeno) throws Exception {
		final File f = tmp.newFolder(jmeno);
		Assert.assertTrue(f.isDirectory());
		return new Filex(f, false, true);
	}
}
