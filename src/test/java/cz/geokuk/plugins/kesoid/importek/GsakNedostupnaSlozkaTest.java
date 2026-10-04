package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mvc.GccomNick;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;

/** Dočasně nedostupná složka GSAKu nemění seznam známých databází; vypnutý GSAK ano. */
public class GsakNedostupnaSlozkaTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final List<Set<File>> zarazeno = new ArrayList<>();

	@Test
	public void nedostupnaSlozkaNicNemeni() throws Exception {
		final MultiNacitac nacitac = new MultiNacitac(model());
		nacitac.setRootDirs(true, null, null, new File(tmp.getRoot(), "odpojeny-disk"), Collections.emptySet());
		nacitac.nacti(null, new Genom());
		Assert.assertEquals(Collections.emptyList(), zarazeno);
	}

	@Test
	public void vypnutyGsakJePrazdny() throws Exception {
		final MultiNacitac nacitac = new MultiNacitac(model());
		nacitac.setRootDirs(true, null, null, null, Collections.emptySet());
		nacitac.nacti(null, new Genom());
		Assert.assertEquals(Collections.singletonList(Collections.emptySet()), zarazeno);
	}

	@Test
	public void dostupnaPrazdnaSlozkaJePrazdna() throws Exception {
		final MultiNacitac nacitac = new MultiNacitac(model());
		nacitac.setRootDirs(true, null, null, tmp.newFolder("gsak"), Collections.emptySet());
		nacitac.nacti(null, new Genom());
		Assert.assertEquals(Collections.singletonList(Collections.emptySet()), zarazeno);
	}

	private KesoidModel model() {
		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		final KesoidModel model = new KesoidModel() {
			@Override
			public GccomNick getGccomNick() {
				return new GccomNick("Ja", 42);
			}

			@Override
			public void zaradGsakDatabaze(final Set<File> databaze) {
				zarazeno.add(databaze);
			}

			@Override
			public void zaradOpensakDatabaze(final Set<File> databaze) {}
		};
		model.inject(progress);
		model.inject(new KesoidPluginManager());
		return model;
	}
}
