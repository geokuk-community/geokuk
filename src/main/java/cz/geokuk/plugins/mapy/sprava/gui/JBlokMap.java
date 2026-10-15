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
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
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

	private final JMapyPopup obsah = new JMapyPopup();
	private final transient PopupNadBlokem popup = new PopupNadBlokem(this, () -> {});
	private final Timer tocitko = new Timer(80, e -> repaint());
	private transient KachleModel kachleModel;
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
			public boolean isObrysDostupny() {
				return true;
			}

			@Override
			public boolean isObrysPokryti() {
				return kachleModel.isObrysPokryti();
			}

			@Override
			public void setObrysPokryti(final boolean zapnuto) {
				kachleModel.setObrysPokryti(zapnuto);
			}
		});
		nactiSlozku();
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
		return kachleModel == null ? null : kachleModel.getUmisteniSouboru().getOfflineMapyDir().getEffectiveFile();
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
				return SouboryMap.nacti(slozka, KatalogMap.vestaveny());
			}

			@Override
			protected void done() {
				cteni = null;
				try {
					obnov(get());
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
