package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.*;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.UzivatelskeMapyPristup;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;

/** Odkaz rady v mapě: platí jen tam, kde je rada vidět. */
public class JKachlovnikPresCeleRadaTest {

	@After
	public void tearDown() {
		UzivatelskeMapyPristup.vycisti();
	}

	/** Po přepnutí na podklad bez atribuce (rada se nekreslí) nesmí zůstat neviditelný odkaz, klik nesmí nic stáhnout. */
	@Test
	public void poPrepnutiPodkladuOdkazZmizi() {
		final AtomicReference<EKaType> podklad = new AtomicReference<>(EKaType.OFFLINE_MF);
		final JKachlovnikPresCele k = new JKachlovnikPresCele() {
			private static final long serialVersionUID = 1L;

			@Override
			protected EKaType getKachloType() {
				return podklad.get();
			}

			@Override
			protected KachleModel getKachleModel() {
				return new KachleModel() {
					@Override
					public boolean jePodrobnaOfflineMapa(final Wgs bod, final int moumer) {
						return false;
					}

					@Override
					public String getAtribuceOfflineMapy() {
						return "© přispěvatelé OpenStreetMap";
					}
				};
			}
		};
		final int[] stazeni = { 0 };
		k.inject(new SpravaMapModel() {
			@Override
			public boolean stahni(final cz.geokuk.plugins.mapy.sprava.KatalogovaMapa mapa) {
				stazeni[0]++;
				return true;
			}
		});
		k.setSize(800, 600);
		k.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		k.setSoord(new Coord(12, new Wgs(50.08, 14.42).toMou(), new Dimension(800, 600), 0));
		kresli(k);
		final Point odkaz = najdiOdkaz(k);
		Assert.assertNotNull("rada nad offline mapou nabízí stažení", odkaz);

		podklad.set(UzivatelskeMapyPristup.sHromadnymStahovanim("bezatribuce", "http://127.0.0.1:1/{z}/{x}/{y}", 0, 18));
		kresli(k);
		Assert.assertFalse(k.jeNadOdkazemRady(odkaz));
		Assert.assertFalse(k.klikNaRadu(odkaz));
		Assert.assertEquals(0, stazeni[0]);
	}

	/** U hranice nabízí rada vedle mapy pod středem i sousední mapy, každá má svůj odkaz. */
	@Test
	public void radaNabidneVicMapVeVyrezu() {
		final JKachlovnikPresCele k = new JKachlovnikPresCele() {
			private static final long serialVersionUID = 1L;

			@Override
			protected EKaType getKachloType() {
				return EKaType.OFFLINE_MF;
			}

			@Override
			protected KachleModel getKachleModel() {
				return new KachleModel() {
					@Override
					public boolean jePodrobnaOfflineMapa(final Wgs bod, final int moumer) {
						return false;
					}

					@Override
					public String getAtribuceOfflineMapy() {
						return "© přispěvatelé OpenStreetMap";
					}
				};
			}
		};
		final java.util.List<String> stazeno = new java.util.ArrayList<>();
		k.inject(new SpravaMapModel() {
			@Override
			public boolean stahni(final cz.geokuk.plugins.mapy.sprava.KatalogovaMapa mapa) {
				stazeno.add(mapa.getId());
				return true;
			}
		});
		k.setSize(800, 600);
		k.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		k.setSoord(new Coord(9, new Wgs(50.78, 14.2).toMou(), new Dimension(800, 600), 0));
		kresli(k);
		for (int y = 0; y < 300; y += 2) {
			k.klikNaRadu(new Point(400, y));
		}
		Assert.assertEquals("mapa pod středem je první", "europe/czech-republic", stazeno.get(0));
		Assert.assertTrue(stazeno.toString(), stazeno.contains("europe/germany/sachsen"));
		Assert.assertTrue(stazeno.toString(), new java.util.HashSet<>(stazeno).size() >= 2);
	}

	private static void kresli(final JKachlovnikPresCele k) {
		final BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		k.paintChildren(g);
		g.dispose();
	}

	private static Point najdiOdkaz(final JKachlovnikPresCele k) {
		for (int y = 0; y < 200; y += 2) {
			for (int x = 0; x < 800; x += 4) {
				if (k.jeNadOdkazemRady(new Point(x, y))) {
					return new Point(x, y);
				}
			}
		}
		return null;
	}

	/** Plná atribuce nainstalované vrstvy (povinná věta u Copernicu) se ukáže jen u stažené vrstvy. */
	@Test
	public void atribuceNainstalovanychVrstev() {
		final String veta = "Povinná věta Copernicus © DLR";
		final cz.geokuk.plugins.mapy.sprava.KatalogVrstev katalog = cz.geokuk.plugins.mapy.sprava.AtribuceVrstevProGui.katalog(veta);
		final boolean[] mame = { false };
		final JKachlovnikPresCele k = new JKachlovnikPresCele();
		k.inject(new SpravaMapModel() {
			@Override
			public cz.geokuk.plugins.mapy.sprava.KatalogVrstev getKatalogVrstev() {
				return katalog;
			}

			@Override
			public cz.geokuk.plugins.mapy.sprava.StavVrstvy getStav(final cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva v) {
				return mame[0] ? cz.geokuk.plugins.mapy.sprava.AtribuceVrstevProGui.mame(v) : cz.geokuk.plugins.mapy.sprava.StavVrstvy.novy(v);
			}
		});
		Assert.assertTrue("vrstva není stažená, atribuce se neukazuje", k.atribuceVrstev().isEmpty());
		mame[0] = true;
		Assert.assertEquals(java.util.Collections.singletonList(veta), k.atribuceVrstev());
	}
}
