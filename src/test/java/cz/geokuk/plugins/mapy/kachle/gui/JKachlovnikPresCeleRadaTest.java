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
		final JKachlovnikPresCele k = new JKachlovnikPresCele() {
			private static final long serialVersionUID = 1L;

			@Override
			protected EKaType getKachloType() {
				return EKaType.OFFLINE_MF;
			}
		};
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
		Assert.assertFalse("vrstva není stažená, atribuce se neukazuje", k.plnaAtribuce().contains(veta));
		mame[0] = true;
		Assert.assertTrue(k.plnaAtribuce().contains(veta));
	}

	private static JKachlovnikPresCele sReliefem(final EKaType podklad) {
		final JKachlovnikPresCele k = new JKachlovnikPresCele() {
			private static final long serialVersionUID = 1L;

			@Override
			protected EKaType getKachloType() {
				return podklad;
			}
		};
		k.onEvent(new cz.geokuk.plugins.mapy.kachle.ReliefCuzkEvent(true));
		return k;
	}

	/** Zapnutý reliéf přidá do atribuce ČÚZK; nad podkladem ČÚZK se neopakuje. */
	@Test
	public void reliefCuzkVAtribuci() {
		final String cuzk = EKaType.RELIEF_CUZK.getAtribuce();
		final JKachlovnikPresCele osm = sReliefem(EKaType.OPEN_STREET);
		Assert.assertTrue(osm.zkratkyAtribuce().toString(), osm.zkratkyAtribuce().containsAll(AtribuceMapy.zkrat(cuzk)));
		Assert.assertTrue(osm.plnaAtribuce().contains(JKachlovnikPresCele.ATRIBUCE_RELIEFU));
		final JKachlovnikPresCele ztm = sReliefem(EKaType.CUZK_ZTM);
		Assert.assertEquals(java.util.Collections.singletonList(cuzk), ztm.plnaAtribuce());
		Assert.assertEquals(AtribuceMapy.zkrat(cuzk), ztm.zkratkyAtribuce());
		final JKachlovnikPresCele bez = new JKachlovnikPresCele() {
			private static final long serialVersionUID = 1L;

			@Override
			protected EKaType getKachloType() {
				return EKaType.OPEN_STREET;
			}
		};
		Assert.assertFalse(bez.plnaAtribuce().contains(JKachlovnikPresCele.ATRIBUCE_RELIEFU));
	}

	/** Rada nabízí na řádku dvě oddělená místa ke kliku: Stáhnout mapu a Stáhnout mapu a vrstvy. */
	@Test
	public void radaMaNaRadkuDveOblastiKKliku() {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
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
		k.inject(new SpravaMapModel() {
			@Override
			public cz.geokuk.plugins.mapy.sprava.KatalogVrstev getKatalogVrstev() {
				return cz.geokuk.plugins.mapy.sprava.VrstvyProGui.katalogCesko();
			}
		});
		k.setSize(800, 600);
		k.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		k.setSoord(new Coord(12, new Wgs(50.08, 14.42).toMou(), new Dimension(800, 600), 0));
		kresli(k);
		final Point prvni = najdiOdkaz(k);
		Assert.assertNotNull(prvni);
		int useku = 0;
		boolean uvnitr = false;
		for (int x = 0; x < 800; x++) {
			final boolean over = k.jeNadOdkazemRady(new Point(x, prvni.y));
			if (over && !uvnitr) {
				useku++;
			}
			uvnitr = over;
		}
		Assert.assertEquals("dvě oddělená místa ke kliku na řádku rady", 2, useku);
	}
}
