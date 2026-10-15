package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.*;

import cz.geokuk.plugins.mapy.KachleUmisteniSouboruChangedEvent;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.ObrysPokrytiEvent;
import cz.geokuk.plugins.mapy.kachle.OfflineMapaChangedEvent;
import cz.geokuk.core.coord.VyrezChangedEvent;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.SpravaMapEvent;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.gui.PopupNadBlokem;
import cz.geokuk.util.process.BrowserOpener;

import lombok.extern.slf4j.Slf4j;

/** Blok „Mapy“ ve stavovém řádku: souhrnná ikona stavu offline map, při stahování procenta; najetí myší nebo klik otevře popup Mapy. */
@Slf4j
public class JBlokMap extends JLabel {

	private static final long serialVersionUID = 1L;

	static final String TEXT = "Mapy";
	/** Nejdelší text bloku; šířka se pro něj drží, aby procenta při stahování neposouvala sousední bloky. */
	static final String NEJDELSI_TEXT = TEXT + " 100 %";

	/** Čtení složky; v testech se dá nahradit. */
	transient java.util.function.Function<File, List<RadekMapy>> cteniSlozky = f -> SouboryMap.nacti(f, KatalogMap.vestaveny());
	private transient java.util.function.Supplier<File> zdrojSlozky = () -> null;

	private final JMapyPopup obsah = new JMapyPopup();
	private final transient PopupNadBlokem popup = new PopupNadBlokem(this, () -> {});
	private final Timer tocitko = new Timer(80, e -> repaint());
	private transient KachleModel kachleModel;
	private transient SpravaMapModel spravaMap;
	/** Mapy ve složce podle posledního čtení. */
	private transient List<RadekMapy> soubory = java.util.Collections.emptyList();
	private transient Wgs stred;
	/** Čtení složky běží, další změna počká na jeho konec. */
	private transient SwingWorker<List<RadekMapy>, Void> cteni;
	private boolean znovu;

	public JBlokMap() {
		super(TEXT);
		setHorizontalTextPosition(SwingConstants.LEFT);
		setIconTextGap(4);
		setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
		obnov(java.util.Collections.emptyList());
		obsah.setPoZmeneObsahu(() -> {
			if (popup.viditelny() == obsah) {
				popup.ukaz(obsah, this, true);
			}
		});
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(final MouseEvent e) {
				popup.naCil(obsah, JBlokMap.this::ukaz, System.currentTimeMillis());
			}

			@Override
			public void mouseExited(final MouseEvent e) {
				popup.opustenCil(obsah);
			}

			@Override
			public void mouseClicked(final MouseEvent e) {
				ukaz();
			}
		});
	}

	public void setOvladani(final OvladaniMap ovladani) {
		obsah.setOvladani(ovladani);
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
		zdrojSlozky = () -> kachleModel.getUmisteniSouboru().getOfflineMapyDir().getEffectiveFile();
		setOvladani(new OvladaniMap() {
			@Override
			public void otevriSlozku() {
				final File slozka = slozka();
				if (slozka == null) {
					return;
				}
				new Thread(() -> {
					if (!slozka.isDirectory() && !slozka.mkdirs()) {
						log.warn("Složku offline map {} nejde založit", slozka);
						return;
					}
					try {
						BrowserOpener.displayFile(slozka.toURI().toURL());
					} catch (final MalformedURLException e) {
						FExceptionDumper.dump(e, EExceptionSeverity.WORKARROUND, "Otevření složky offline map");
					}
				}, "Otevření složky offline map").start();
			}

			@Override
			public void stahni(final String id) {
				if (spravaMap != null) {
					spravaMap.getKatalog().podleId(id).ifPresent(spravaMap::stahni);
				}
			}

			@Override
			public void zrusStahovani() {
				if (spravaMap != null) {
					spravaMap.zrusStahovani();
				}
			}

			@Override
			public void zkontroluj(final String id) {
				if (spravaMap != null) {
					spravaMap.getKatalog().podleId(id).ifPresent(spravaMap::zkontroluj);
				}
			}

			@Override
			public boolean isObrysDostupny() {
				return true;
			}

			@Override
			public boolean isObrysPokryti() {
				return kachleModel.isObrysPokryti();
			}

			@Override
			public void zvyrazniMapu(final String klic) {
				kachleModel.setZvyrazneniMapy(klic);
			}

			@Override
			public void setObrysPokryti(final boolean zapnuto) {
				kachleModel.setObrysPokryti(zapnuto);
			}
		});
		nactiSlozku();
	}

	public void inject(final SpravaMapModel spravaMap) {
		this.spravaMap = spravaMap;
	}

	public void onEvent(final SpravaMapEvent event) {
		prestav();
	}

	/** Střed výřezu určuje mapy „Pro zobrazené místo“; popup se přestaví, jen když se jejich výběr změní. */
	public void onEvent(final VyrezChangedEvent event) {
		stred = event.getMoord().getMoustred().toWgs();
		prestav();
	}

	private void prestav() {
		obnov(spravaMap == null ? soubory : RadkyMap.sestav(spravaMap.getKatalog(), spravaMap::getStav, soubory, stred));
	}

	public void onEvent(final ObrysPokrytiEvent event) {
		obsah.setObrysZapnuty(event.isZapnuto());
	}

	public void onEvent(final OfflineMapaChangedEvent event) {
		nactiSlozku();
	}

	public void onEvent(final KachleUmisteniSouboruChangedEvent event) {
		nactiSlozku();
	}

	private File slozka() {
		return zdrojSlozky.get();
	}

	void setZdrojSlozky(final java.util.function.Supplier<File> zdrojSlozky) {
		this.zdrojSlozky = zdrojSlozky;
	}

	@Override
	public java.awt.Dimension getPreferredSize() {
		final java.awt.Dimension d = super.getPreferredSize();
		final java.awt.Insets ins = getInsets();
		final int nejvic = getFontMetrics(getFont()).stringWidth(NEJDELSI_TEXT) + getIconTextGap() + IkonyMap.VELIKOST + ins.left + ins.right;
		d.width = Math.max(d.width, nejvic);
		return d;
	}

	/** Přečte mapy ve složce mimo EDT; změny během čtení se sloučí do jednoho dalšího čtení. */
	void nactiSlozku() {
		if (cteni != null) {
			znovu = true;
			return;
		}
		final File slozka = slozka();
		cteni = new SwingWorker<List<RadekMapy>, Void>() {
			@Override
			protected List<RadekMapy> doInBackground() {
				return cteniSlozky.apply(slozka);
			}

			@Override
			protected void done() {
				cteni = null;
				try {
					soubory = get();
					prestav();
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (final ExecutionException e) {
					FExceptionDumper.dump(e.getCause(), EExceptionSeverity.WORKARROUND, "Čtení složky offline map");
				}
				if (znovu) {
					znovu = false;
					nactiSlozku();
				}
			}
		};
		cteni.execute();
	}

	public void obnov(final List<RadekMapy> radky) {
		final RadekMapy.Stav stav = souhrn(radky);
		setIcon(IkonyMap.pro(stav));
		final RadekMapy stahovana = radky.stream().filter(r -> r.stav == RadekMapy.Stav.STAHUJE).findFirst().orElse(null);
		setText(stahovana != null && stahovana.velikost > 0 ? TEXT + " " + stahovana.procenta() + " %" : TEXT);
		setToolTipText(tooltip(radky, stav, stahovana));
		if (stav == RadekMapy.Stav.STAHUJE) {
			tocitko.start();
		} else {
			tocitko.stop();
		}
		obsah.obnov(radky);
	}

	/** Nejdůležitější stav: stahování, chyba, novější verze, pak mapy ve složce; bez map šedá. */
	static RadekMapy.Stav souhrn(final List<RadekMapy> radky) {
		RadekMapy.Stav vysledek = RadekMapy.Stav.NEMAME;
		for (final RadekMapy r : radky) {
			if (poradi(r.stav) < poradi(vysledek)) {
				vysledek = r.stav;
			}
		}
		return vysledek;
	}

	private static int poradi(final RadekMapy.Stav s) {
		switch (s) {
		case STAHUJE:
			return 0;
		case CHYBA:
			return 1;
		case NOVEJSI:
			return 2;
		case AKTUALNI:
			return 3;
		case NEMAME:
		default:
			return 4;
		}
	}

	static String tooltip(final List<RadekMapy> radky, final RadekMapy.Stav stav, final RadekMapy stahovana) {
		final long veSlozce = radky.stream().filter(RadekMapy::isVeSlozce).count();
		switch (stav) {
		case STAHUJE:
			return "Stahuje se mapa " + stahovana.nazev + ".";
		case CHYBA:
			return "Některá offline mapa má problém.";
		case NOVEJSI:
			return "Pro některou offline mapu jsou novější data.";
		default:
			return veSlozce == 0 ? "Offline mapy: zatím žádná" : "Offline mapy: " + veSlozce + " ve složce";
		}
	}

	public void ukaz() {
		if (popup.viditelny() == obsah) {
			return;
		}
		// Hlídání složky běží jen u offline podkladu; mapu nakopírovanou při jiném podkladu tak popup ukáže hned.
		nactiSlozku();
		if (spravaMap != null) {
			spravaMap.obnovStavy();
		}
		popup.ukaz(obsah, this, false);
	}

	public void zavri() {
		popup.zavri();
	}

	JMapyPopup getObsah() {
		return obsah;
	}

	@Override
	public void removeNotify() {
		tocitko.stop();
		popup.zastav();
		super.removeNotify();
	}
}
