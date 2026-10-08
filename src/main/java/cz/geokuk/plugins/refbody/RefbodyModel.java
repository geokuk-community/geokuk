package cz.geokuk.plugins.refbody;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.program.FPref;
import cz.geokuk.framework.Factory;
import cz.geokuk.framework.Model0;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RefbodyModel extends Model0 {



	private static final Wgs DEFAULTNI_DOMACI_SOURADNICE = new Wgs(49.8, 15.5);

	private Wgs hc;

	private Factory factory;

	private KesoidModel kesoidModel;

	public Wgs getHc() {
		return hc;
	}

	@Override
	public void inject(final Factory factory) {
		this.factory = factory;
	}

	public void inject(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
	}

	/** Referenční body z geohome.ini GeoGetu; soubor se čte mimo EDT (složka může být na neodpovídajícím disku), akce pak dostane {@code hotovo} na EDT. */
	public void nactiNaPozadi(final Consumer<List<NaKonkretniBodAction>> hotovo) {
		if (!kesoidModel.getUmisteniSouboru().getGeogetDataDir().isActive()) {
			return;
		}
		final File file = new File(kesoidModel.getUmisteniSouboru().getGeogetDataDir().getEffectiveFile(), "geohome.ini");
		final Thread vlakno = new Thread(() -> {
			final List<RefBod> body = precti(file);
			SwingUtilities.invokeLater(() -> {
				final List<NaKonkretniBodAction> akce = new ArrayList<>();
				for (final RefBod bod : body) {
					akce.add(factory.init(new NaKonkretniBodAction(bod.nazev, bod.wgs)));
				}
				hotovo.accept(akce);
			});
		}, "Referenční body z GeoGetu");
		vlakno.setDaemon(true);
		vlakno.start();
	}

	static final class RefBod {
		final String nazev;
		final Wgs wgs;

		RefBod(final String nazev, final Wgs wgs) {
			this.nazev = nazev;
			this.wgs = wgs;
		}
	}

	static List<RefBod> precti(final File file) {
		final List<RefBod> list = new ArrayList<>();
		try {
			if (file.canRead()) {
				// TODO prozkoumat, zda opravdu geogetí data jsou v tomto kódování
				try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "CP1250"))) {
					String line;
					while ((line = br.readLine()) != null) {
						final String[] aa = line.split(" +", 3);
						if (aa.length != 3) {
							continue;
						}
						try {
							list.add(new RefBod(aa[2], new Wgs(Double.parseDouble(aa[0]), Double.parseDouble(aa[1]))));
						} catch (final Throwable e) {
							FExceptionDumper.dump(e, EExceptionSeverity.WORKARROUND, "Čtení souboru " + file + ", řádek " + (list.size() + 1));
						}
					}
				}
			} else {
				// Složka GeoGetu bez geohome.ini (prázdná, jen databáze) je v pořádku, referenční body z GeoGetu prostě nejsou.
				log.info("Soubor \"" + file + "\" nelze číst, referenční body z GeoGetu nejsou.");
			}
		} catch (final IOException e) {
			FExceptionDumper.dump(e, EExceptionSeverity.WORKARROUND, "Čtení souboru " + file + ", řádek " + (list.size() + 1));
		}
		return list;
	}

	public void setHc(final Wgs hc) {
		if (hc.equals(this.hc)) {
			return;
		}
		this.hc = hc;
		currPrefe().node(FPref.DOMACI_SOURADNICE_node).putWgs(FPref.HC_value, hc);
		fire(new DomaciSouradniceSeZmenilyEvent(hc));
	}

	@Override
	protected void initAndFire() {
		setHc(currPrefe().node(FPref.DOMACI_SOURADNICE_node).getWgs(FPref.HC_value, DEFAULTNI_DOMACI_SOURADNICE));
	}

}
