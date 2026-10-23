package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.FontMetrics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
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
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.PokrytiMap;
import cz.geokuk.plugins.mapy.sprava.SpravaMapEvent;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.gui.PopupNadBlokem;
import cz.geokuk.util.process.OtevreniSlozky;

import lombok.extern.slf4j.Slf4j;

/** Blok „Mapy“ ve stavovém řádku: souhrnná ikona stavu offline map, při stahování procenta; najetí myší nebo klik otevře popup Mapy. */
@Slf4j
public class JBlokMap extends JLabel {

	private static final long serialVersionUID = 1L;

	static final String TEXT = "Mapy";
	/** Nejdelší text bloku; šířka se pro něj drží, aby procenta při stahování neposouvala sousední bloky. */
	static final String NEJDELSI_TEXT = TEXT + " 100 %";
	static final String KONTROLUJE = "Kontroluji…";
	static final String KONTROLUJE_TIP = "Zjišťuji na serveru, jestli jsou pro offline mapy novější data.";

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
	/** Mapy pokrývající střed výřezu podle posledního sestavení řádků. */
	private transient java.util.Set<String> proMisto = java.util.Collections.emptySet();
	/** Čtení složky běží, další změna počká na jeho konec. */
	private transient SwingWorker<List<RadekMapy>, Void> cteni;
	private boolean znovu;
	private volatile boolean stinovaniDostupne;
	private volatile long volne = -1;
	private transient volatile List<PokrytiMap.DvojitaMapa> dvojite = java.util.Collections.emptyList();

	static final String PREFE_NODE = "offline-mapy-popup";
	static final String PREFE_SLOZKA_VOLBA = "veSlozceVolba";
	static final String PREFE_SLOZKA_OTEVRENA = "veSlozceOtevrena";

	/** Zapamatované sbalení oddílu Ve složce, null když uživatel nikdy nevolil. */
	static java.util.Optional<Boolean> nactiStavSlozky() {
		final cz.geokuk.framework.MyPreferences p = cz.geokuk.framework.MyPreferences.current().node(PREFE_NODE);
		return p.getBoolean(PREFE_SLOZKA_VOLBA, false) ? java.util.Optional.of(p.getBoolean(PREFE_SLOZKA_OTEVRENA, true)) : java.util.Optional.empty();
	}

	static void ulozStavSlozky(final Boolean otevrena) {
		final cz.geokuk.framework.MyPreferences p = cz.geokuk.framework.MyPreferences.current().node(PREFE_NODE);
		p.putBoolean(PREFE_SLOZKA_VOLBA, true);
		p.putBoolean(PREFE_SLOZKA_OTEVRENA, otevrena);
	}

	static java.util.Set<String> nactiRozbaleneZeme(final String klic) {
		return cz.geokuk.framework.MyPreferences.current().node(PREFE_NODE).getStringSet(klic, java.util.Collections.emptySet());
	}

	static void ulozRozbaleneZeme(final String klic, final java.util.Set<String> zeme) {
		cz.geokuk.framework.MyPreferences.current().node(PREFE_NODE).putStringSet(klic, zeme);
	}

	public JBlokMap() {
		super(TEXT);
		setHorizontalTextPosition(SwingConstants.LEFT);
		setIconTextGap(4);
		setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
		obsah.nactiStavSlozky = JBlokMap::nactiStavSlozky;
		obsah.ulozStavSlozky = JBlokMap::ulozStavSlozky;
		obsah.pamatujRozbaleni(JBlokMap::nactiRozbaleneZeme, JBlokMap::ulozRozbaleneZeme);
		cz.geokuk.plugins.mapy.pruvodce.SpousteniPruvodce.setPoZmene(() -> javax.swing.SwingUtilities.invokeLater(() -> obsah.obnov(obsah.getRadky())));
		obsah.setDostupnaVyska(() -> isShowing() ? getLocationOnScreen().y - popup.horniHranice() : -1);
		obsah.setZobrazeny(() -> popup.viditelny() == obsah);
		obnov(java.util.Collections.emptyList());
		popup.setDrzOtevreny(obsah::isHledaniAktivni);
		popup.setPoZavreni(obsah::popupZavren);
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
					OtevreniSlozky.otevri(slozka);
				}, "Otevření složky offline map").start();
			}

			@Override
			public void stahni(final String id) {
				if (spravaMap != null) {
					spravaMap.getKatalog().podleId(id).ifPresent(spravaMap::stahni);
				}
			}

			@Override
			public void stahniVse(final String nazevZeme, final String celekKlic, final List<String> klice) {
				if (spravaMap == null) {
					return;
				}
				popup.zavri();
				final java.awt.Window okno = javax.swing.SwingUtilities.getWindowAncestor(JBlokMap.this);
				new cz.geokuk.plugins.mapy.pruvodce.JStahnoutVse(okno instanceof java.awt.Frame ? (java.awt.Frame) okno : null,
						new cz.geokuk.plugins.mapy.pruvodce.ProgramPruvodceKontext(spravaMap, null, kachleModel), nazevZeme, celekKlic, klice).setVisible(true);
			}

			@Override
			public java.util.List<String> souhrnDavky() {
				return cz.geokuk.plugins.mapy.pruvodce.SpousteniPruvodce.souhrn();
			}

			@Override
			public void zkusZnovuDavku() {
				cz.geokuk.plugins.mapy.pruvodce.SpousteniPruvodce.zkusZnovu();
			}

			@Override
			public void zavriSouhrnDavky() {
				cz.geokuk.plugins.mapy.pruvodce.SpousteniPruvodce.zavriSouhrn();
			}

			@Override
			public void dostahnoutVrstvy() {
				if (spravaMap == null) {
					return;
				}
				popup.zavri();
				final java.awt.Window okno = javax.swing.SwingUtilities.getWindowAncestor(JBlokMap.this);
				cz.geokuk.plugins.mapy.pruvodce.JStahnoutVse.dostahnoutVrstvy(okno instanceof java.awt.Frame ? (java.awt.Frame) okno : null,
						new cz.geokuk.plugins.mapy.pruvodce.ProgramPruvodceKontext(spravaMap, null, kachleModel)).setVisible(true);
			}

			@Override
			public void stahniVrstvu(final String id) {
				if (spravaMap != null) {
					spravaMap.getKatalogVrstev().podleId(id).ifPresent(spravaMap::stahni);
				}
			}

			@Override
			public void smazVrstvu(final String id) {
				if (spravaMap == null) {
					return;
				}
				spravaMap.getKatalogVrstev().podleId(id).ifPresent(v -> smazSPotvrzenim("Smazat vrstvu " + v.getNazev(), SmazaniSouboru.souboryVrstvy(spravaMap.getSlozkaVrstev(), v)));
			}

			@Override
			public void smazDvojite(final String idCelku, final boolean regiony) {
				if (spravaMap == null) {
					return;
				}
				for (final PokrytiMap.DvojitaMapa d : PokrytiMap.dvojiteMapy(slozka(), spravaMap.getKatalog())) {
					if (!d.getCelek().getId().equals(idCelku)) {
						continue;
					}
					final List<java.io.File> soubory = SmazaniSouboru.souboryDvojite(slozka(), d, regiony);
					if (soubory.isEmpty()) {
						return;
					}
					smazSPotvrzenim(regiony ? "Smazat regiony země " + d.getCelek().getNazev() : "Smazat celou zemi " + d.getCelek().getNazev(), soubory);
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
			public void zkontrolujKatalogVrstev() {
				if (spravaMap != null) {
					spravaMap.zkontrolujKatalogVrstev();
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
			public boolean isStinovaniDostupne() {
				return stinovaniDostupne;
			}

			@Override
			public boolean isStinovani() {
				return kachleModel.isStinovaniTerenu();
			}

			@Override
			public void setStinovani(final boolean zapnuto) {
				kachleModel.setStinovaniTerenu(zapnuto);
			}

			@Override
			public boolean isPrekryvTrasDostupny() {
				return true;
			}

			@Override
			public boolean isPrekryvTras() {
				return kachleModel.isPrekryvTras();
			}

			@Override
			public void setPrekryvTras(final boolean zapnuto) {
				kachleModel.setPrekryvTras(zapnuto);
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

	/** Menu Mapy > Offline mapy…: panel se otevře a fokus dostane jeho první ovladatelná část, takže zůstane otevřený, dokud je v něm fokus. */
	public void onEvent(final cz.geokuk.plugins.mapy.sprava.OtevriPanelMapEvent event) {
		ukaz();
		obsah.zaostri();
	}

	public void onEvent(final SpravaMapEvent event) {
		prestav();
	}

	public void onEvent(final cz.geokuk.plugins.mapy.sprava.SpravaVrstevEvent event) {
		prestav();
	}

	public void onEvent(final cz.geokuk.plugins.mapy.sprava.KatalogVrstevEvent event) {
		prestav();
	}

	public void onEvent(final cz.geokuk.plugins.mapy.sprava.StavyMapEvent event) {
		prestav();
	}

	/** Střed výřezu určuje mapy „Pro zobrazené místo“; popup se přestaví, jen když se jejich výběr změní. */
	public void onEvent(final VyrezChangedEvent event) {
		setStred(event.getMoord().getMoustred().toWgs());
	}

	void setStred(final Wgs novy) {
		final java.util.Set<String> nove = mapyProMisto(novy);
		stred = novy;
		if (!nove.equals(proMisto)) {
			proMisto = nove;
			prestav();
		}
	}

	private java.util.Set<String> mapyProMisto(final Wgs s) {
		final java.util.Set<String> v = new java.util.HashSet<>();
		if (spravaMap != null && s != null) {
			for (final KatalogovaMapa m : spravaMap.getKatalog().getMapy()) {
				if (m.getPokryti().obsahuje(s)) {
					v.add(m.getId());
				}
			}
		}
		return v;
	}

	private void prestav() {
		obnov(spravaMap == null ? soubory : RadkyMap.sestav(spravaMap.getKatalog(), spravaMap::getStav, soubory, stred, spravaMap.getKatalogVrstev(), spravaMap::getStav));
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
		final FontMetrics fm = getFontMetrics(getFont());
		final int nejvic = Math.max(fm.stringWidth(NEJDELSI_TEXT), fm.stringWidth(KONTROLUJE)) + getIconTextGap() + IkonyMap.VELIKOST + ins.left + ins.right;
		d.width = Math.max(d.width, nejvic);
		return d;
	}

	/** Volné místo na disku, na kterém je (nebo bude) složka; -1 když není známé. */
	static long volneMisto(final File slozka) {
		File f = slozka;
		while (f != null && !f.exists()) {
			f = f.getParentFile();
		}
		return f == null ? -1 : bezNuly(f.getUsableSpace());
	}

	/** Nulu vrací systém i při chybě; plný disk od neznámého nejde rozeznat, tak 0 platí jako neznámé. */
	static long bezNuly(final long volne) {
		return volne <= 0 ? -1 : volne;
	}

	/** Potvrzení smazání: ukáže přesně, které soubory se smažou. Pro testy se dá nahradit. */
	transient java.util.function.BiFunction<String, String, Boolean> potvrzeni = (titulek, text) -> JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this), text, titulek,
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;

	/** Smaže soubory po potvrzení; otevřené offline mapy se předtím zavřou (Windows otevřený soubor nesmaže), mazání běží mimo EDT. */
	void smazSPotvrzenim(final String titulek, final List<File> soubory) {
		if (soubory.isEmpty() || spravaMap != null && (spravaMap.getBeziciStahovani().isPresent() || spravaMap.getBeziciStahovaniVrstvy().isPresent())
				|| !potvrzeni.apply(titulek, "Smažou se tyto soubory:\n" + SmazaniSouboru.popis(soubory) + "\nSmazání nejde vrátit.")) {
			return;
		}
		if (kachleModel != null && kachleModel.getZiskavac() != null) {
			kachleModel.getZiskavac().getOfflineMapy().zavri();
			kachleModel.getZiskavac().getOfflineTrasy().zavri();
		}
		new SwingWorker<List<File>, Void>() {
			@Override
			protected List<File> doInBackground() {
				return SmazaniSouboru.smaz(soubory);
			}

			@Override
			protected void done() {
				try {
					final List<File> nesmazane = get();
					if (!nesmazane.isEmpty()) {
						JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(JBlokMap.this), "Nešlo smazat: " + nesmazane.get(0).getName() + (nesmazane.size() > 1 ? " a další" : ""),
								titulek, JOptionPane.ERROR_MESSAGE);
					}
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (final ExecutionException e) {
					FExceptionDumper.dump(e.getCause(), EExceptionSeverity.WORKARROUND, "Smazání souborů offline map");
				}
				if (spravaMap != null) {
					spravaMap.obnovStavy();
				}
				nactiSlozku();
			}
		}.execute();
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
				stinovaniDostupne = cz.geokuk.plugins.mapy.kachle.podklady.OfflineMapy.maDem(slozka);
				volne = volneMisto(slozka);
				dvojite = spravaMap == null ? java.util.Collections.emptyList() : PokrytiMap.dvojiteMapy(slozka, spravaMap.getKatalog());
				return cteniSlozky.apply(slozka);
			}

			@Override
			protected void done() {
				cteni = null;
				try {
					soubory = get();
					obsah.zmenyNaraz(() -> {
						obsah.setStinovaniDostupne(stinovaniDostupne);
						obsah.setVolneMisto(volne);
						obsah.setDvojite(dvojite);
						prestav();
					});
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
		final RadekMapy stahovana = radky.stream().filter(r -> r.celkovyStav() == RadekMapy.Stav.STAHUJE).findFirst().orElse(null);
		final boolean kontroluje = stav != RadekMapy.Stav.STAHUJE && radky.stream().anyMatch(r -> r.kontroluje);
		if (kontroluje) {
			setText(KONTROLUJE);
			setToolTipText(KONTROLUJE_TIP);
		} else {
			setText(procentaStahovani(stahovana) >= 0 ? TEXT + " " + procentaStahovani(stahovana) + " %" : TEXT);
			setToolTipText(tooltip(radky, stav, stahovana));
		}
		if (stav == RadekMapy.Stav.STAHUJE) {
			tocitko.start();
		} else {
			tocitko.stop();
		}
		obsah.obnov(radky);
	}

	/** Procenta právě stahované mapy nebo vrstvy, -1 když se nic nestahuje nebo velikost není známá. */
	static int procentaStahovani(final RadekMapy stahovana) {
		if (stahovana == null) {
			return -1;
		}
		if (stahovana.stav == RadekMapy.Stav.STAHUJE) {
			return stahovana.velikost > 0 ? stahovana.procenta() : -1;
		}
		final MatriceMap.Bunka vrstva = stahovana.bezicVrstva();
		return vrstva == null || vrstva.getVelikost() <= 0 ? -1 : (int) Math.min(100, vrstva.getStazeno() * 100 / vrstva.getVelikost());
	}

	/** Nejdůležitější stav: stahování, chyba, novější verze, pak mapy ve složce; bez map šedá. */
	static RadekMapy.Stav souhrn(final List<RadekMapy> radky) {
		RadekMapy.Stav vysledek = RadekMapy.Stav.NEMAME;
		for (final RadekMapy r : radky) {
			if (poradi(r.celkovyStav()) < poradi(vysledek)) {
				vysledek = r.celkovyStav();
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
			return (stahovana.stav == RadekMapy.Stav.STAHUJE ? "Stahuje se mapa " : "Stahují se vrstvy k mapě ") + stahovana.nazev + ".";
		case CHYBA:
			return "Některá offline mapa má problém.";
		case NOVEJSI:
			return "Pro některou offline mapu jsou novější data.";
		default:
			return veSlozce == 0 ? TextyMap.ZADNA_MAPA : "Stažené mapy: " + veSlozce;
		}
	}

	/** Tooltip by překryl sousední bloky a říká totéž co popup. */
	@Override
	public String getToolTipText(final MouseEvent e) {
		return popup.otevira(obsah) ? null : super.getToolTipText(e);
	}

	public void ukaz() {
		if (popup.viditelny() == obsah) {
			return;
		}
		// Hlídání složky běží jen u offline podkladu; mapu nakopírovanou při jiném podkladu tak popup ukáže hned.
		nactiSlozku();
		if (spravaMap != null) {
			spravaMap.obnovStavy();
			spravaMap.obnovKatalogVrstevKdyzJeStary();
		}
		obsah.prepocitejVysku();
		obsah.pripravKZobrazeni();
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
