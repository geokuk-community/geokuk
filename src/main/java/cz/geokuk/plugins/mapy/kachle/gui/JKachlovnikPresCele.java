package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva;
import cz.geokuk.plugins.mapy.sprava.SpravaMapEvent;
import cz.geokuk.plugins.mapy.sprava.StavVrstvy;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.plugins.mapy.sprava.gui.RadaOfflineMapy;

public class JKachlovnikPresCele extends JKachlovnik {

	private static final long serialVersionUID = -3170605712662727739L;
	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.core.coord.JSingleSlide0#render(java.awt.Graphics)
	 */

	public JKachlovnikPresCele() {
		super("Hlavní kachlovník", Priority.KACHLE);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.core.coord.JSingleSlide0#createRenderableSlide()
	 */
	@Override
	protected void init(final boolean smimZnovuPouzitKachle) {
		// Okno mohlo přejít na displej s jiným měřítkem.
		final GraphicsConfiguration gc = getGraphicsConfiguration();
		if (gc != null && getKachleModel() != null) {
			getKachleModel().setMeritkoDispleje(gc.getDefaultTransform().getScaleX());
		}
		super.init(smimZnovuPouzitKachle);
	}

	@Override
	public JSingleSlide0 createRenderableSlide() {
		return new JKachlovnikRendrovaci();
	}


	private static final Color ODKAZ = new Color(0x1A5FB4);

	private transient SpravaMapModel spravaMap;
	/** Kde jsou v kachlovníku odkazy rady a mapy, které stahují; prázdné, když se nekreslí. */
	private final transient List<Rectangle> odkazyRady = new ArrayList<>();
	private final transient List<KatalogovaMapa> mapyRady = new ArrayList<>();
	private transient RadaOfflineMapy rada;
	/** Kde je v kachlovníku ikona ⓘ atribuce; null, když se nekreslí. */
	private transient Rectangle ikonaAtribuce;

	public void inject(final SpravaMapModel spravaMap) {
		this.spravaMap = spravaMap;
	}

	public void onEvent(final SpravaMapEvent event) {
		if (!odkazyRady.isEmpty() || rada != null && rada.mapa != null && rada.mapa.getId().equals(event.getMapa().getId())) {
			repaint();
		}
	}

	public void onEvent(final cz.geokuk.plugins.mapy.sprava.SpravaVrstevEvent event) {
		repaint();
	}

	public void onEvent(final cz.geokuk.plugins.mapy.sprava.KatalogVrstevEvent event) {
		repaint();
	}

	public void onEvent(final cz.geokuk.plugins.mapy.sprava.StavyMapEvent event) {
		repaint();
	}

	public void onEvent(final cz.geokuk.plugins.mapy.kachle.ReliefCuzkEvent event) {
		reliefCuzk = event.isZapnuto();
		repaint();
	}

	/** Klik na odkaz rady stáhne nabízenou mapu; vrací, jestli klik patřil odkazu. */
	public boolean klikNaRadu(final Point bod) {
		if (ikonaAtribuce != null && ikonaAtribuce.contains(bod)) {
			ukazPlnouAtribuci();
			return true;
		}
		if (spravaMap == null) {
			return false;
		}
		for (int i = 0; i < odkazyRady.size(); i++) {
			if (odkazyRady.get(i).contains(bod)) {
				spravaMap.stahni(mapyRady.get(i));
				return true;
			}
		}
		return false;
	}

	public boolean jeNadOdkazemRady(final Point bod) {
		return odkazyRady.stream().anyMatch(r -> r.contains(bod)) || ikonaAtribuce != null && ikonaAtribuce.contains(bod);
	}

	/** Zeměpisný obdélník, do kterého se vejdou všechny čtyři rohy výřezu. */
	private RadaOfflineMapy.Vyrez vyrez() {
		final Wgs[] rohy = { getSoord().getMouSZ().toWgs(), getSoord().getMouSV().toWgs(), getSoord().getMouJV().toWgs(), getSoord().getMouJZ().toWgs() };
		double jih = 90;
		double zapad = 180;
		double sever = -90;
		double vychod = -180;
		for (final Wgs r : rohy) {
			jih = Math.min(jih, r.lat);
			sever = Math.max(sever, r.lat);
			zapad = Math.min(zapad, r.lon);
			vychod = Math.max(vychod, r.lon);
		}
		return new RadaOfflineMapy.Vyrez(jih, zapad, sever, vychod);
	}

	static final String RADA_BEZ_SPRAVY = "Offline mapy najdete v bloku Mapy vpravo dole ve stavovém řádku.";

	/** Atribuci kreslí přes kachle vpravo dole, radu, kde vzít offline mapu, nahoře uprostřed. */
	@Override
	protected void paintChildren(final Graphics g) {
		super.paintChildren(g);
		odkazyRady.clear();
		mapyRady.clear();
		rada = null;
		ikonaAtribuce = null;
		if (getKachloType() == null || getKachloType().getAtribuce().isEmpty()) {
			return;
		}
		if (jeRada()) {
			kresliRadu((Graphics2D) g);
		}
		kresliAtribuci((Graphics2D) g);
	}

	/** Atribuce: jeden zkrácený řádek vpravo dole, nezasahuje do měřítka vlevo; plné znění po kliku na ikonu ⓘ. */
	private void kresliAtribuci(final Graphics2D g) {
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setFont(getFont().deriveFont(Font.PLAIN, 10f));
			final FontMetrics fm = g2.getFontMetrics();
			final AtribuceMapy.Rozlozeni r = AtribuceMapy.rozloz(zkratkyAtribuce(), fm, getWidth(), getHeight(), Math.max(120, getWidth() - ODSTUP_MERITKA));
			AtribuceMapy.kresli(g2, r, fm);
			ikonaAtribuce = r.ikona;
		} finally {
			g2.dispose();
		}
	}

	/** Šířka vlevo dole vyhrazená měřítku; atribuce do ní nezasahuje. */
	static final int ODSTUP_MERITKA = 220;

	/** Zkrácené zdroje atribuce: podklad, data offline mapy, vrstvy a překryvy. */
	List<String> zkratkyAtribuce() {
		final List<String> v = new ArrayList<>();
		v.addAll(AtribuceMapy.zkrat(textAtribuce()));
		for (final KatalogovaVrstva k : vrstvyAtribuce()) {
			v.addAll(AtribuceMapy.zkrat(k.getAtribuce()));
		}
		if (prekryvTrasPotrebujeAtribuci()) {
			v.add(AtribuceMapy.OSM);
		}
		if (reliefCuzk) {
			v.addAll(AtribuceMapy.zkrat(EKaType.RELIEF_CUZK.getAtribuce()));
		}
		return AtribuceMapy.slouc(v);
	}

	/** Plné znění atribuce: jeden zdroj na řádek. */
	List<String> plnaAtribuce() {
		final List<String> v = new ArrayList<>();
		v.add(textAtribuce());
		if (prekryvTrasPotrebujeAtribuci()) {
			v.add(JKachlovnikTras.ATRIBUCE);
		}
		if (reliefCuzk && !uzJeVeZneni(v, EKaType.RELIEF_CUZK.getAtribuce())) {
			v.add(ATRIBUCE_RELIEFU);
		}
		for (final KatalogovaVrstva k : vrstvyAtribuce()) {
			final String t = k.getAtribuceDlouha() != null && !k.getAtribuceDlouha().trim().isEmpty() ? k.getAtribuceDlouha().trim() : k.getAtribuce();
			if (t != null && !t.trim().isEmpty() && !v.contains(t.trim()) && !uzJeVeZneni(v, t)) {
				v.add(t.trim());
			}
		}
		return v;
	}

	/** Všechny zdroje z textu už jsou ve znění; jinak by se třeba OpenStreetMap opakovalo. */
	private static boolean uzJeVeZneni(final List<String> zneni, final String text) {
		final List<String> mam = new ArrayList<>();
		zneni.forEach(z -> mam.addAll(AtribuceMapy.zkrat(z)));
		return mam.containsAll(AtribuceMapy.zkrat(text));
	}

	private String textAtribuce() {
		if (getKachloType() == null) {
			return "";
		}
		return getKachloType().isOffline() && getKachleModel() != null ? getKachleModel().getAtribuceOfflineMapy() : getKachloType().getAtribuce();
	}

	/** Přes mapu se kreslí stínovaný reliéf ČÚZK. */
	private boolean reliefCuzk;

	static final String ATRIBUCE_RELIEFU = "Stínovaný reliéf " + EKaType.RELIEF_CUZK.getAtribuce();

	private boolean prekryvTrasPotrebujeAtribuci() {
		return JKachlovnikTras.potrebujeAtribuci(getKachloType()) && getKachleModel() != null && getKachleModel().isPrekryvTras();
	}

	private List<KatalogovaVrstva> vrstvyAtribuce() {
		final List<KatalogovaVrstva> v = new ArrayList<>();
		if (spravaMap == null || getKachloType() == null || !getKachloType().isOffline()) {
			return v;
		}
		for (final KatalogovaVrstva k : spravaMap.getKatalogVrstev().getVrstvy()) {
			if (k.getAtribuce() != null && !k.getAtribuce().trim().isEmpty() && spravaMap.getStav(k).getMistni() == StavVrstvy.Mistni.MAME) {
				v.add(k);
			}
		}
		return v;
	}

	/** Ukáže plné znění atribuce nad ikonou ⓘ. */
	void ukazPlnouAtribuci() {
		final javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		final javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.GridLayout(0, 1, 0, 3));
		panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8));
		for (final String t : plnaAtribuce()) {
			if (!t.trim().isEmpty()) {
				panel.add(new javax.swing.JLabel(t));
			}
		}
		final javax.swing.JButton osm = new javax.swing.JButton("Autorská práva OpenStreetMap");
		osm.setBorderPainted(false);
		osm.setContentAreaFilled(false);
		osm.setForeground(ODKAZ);
		osm.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		osm.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
		osm.addActionListener(e -> {
			try {
				cz.geokuk.util.process.BrowserOpener.displayURL(new java.net.URL("https://www.openstreetmap.org/copyright"));
			} catch (final java.net.MalformedURLException ex) {
				throw new IllegalStateException(ex);
			}
			menu.setVisible(false);
		});
		panel.add(osm);
		menu.add(panel);
		final Rectangle ikona = ikonaAtribuce != null ? ikonaAtribuce : new Rectangle(getWidth() - 20, getHeight() - 16, 16, 14);
		final Dimension d = menu.getPreferredSize();
		menu.show(this, Math.max(0, Math.min(ikona.x + ikona.width - d.width, getWidth() - d.width)), ikona.y - d.height);
	}

	/** Offline mapa a střed výřezu nepokrývá žádná podrobná mapa. */
	boolean jeRada() {
		return getKachloType() != null && getKachloType().isOffline() && getKachleModel() != null && isSoordInitialized() && getSoord() != null
				&& !getKachleModel().jePodrobnaOfflineMapa(getSoord().getMoustred().toWgs(), getSoord().getMoumer());
	}

	private void kresliRadu(final Graphics2D g) {
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			final Font tucne = getFont().deriveFont(Font.BOLD, 12f);
			final Font obycejne = getFont().deriveFont(Font.PLAIN, 12f);
			final FontMetrics fmTucne = g2.getFontMetrics(tucne);
			final FontMetrics fm = g2.getFontMetrics(obycejne);
			rada = spravaMap == null ? null : RadaOfflineMapy.pro(spravaMap.getKatalog(), spravaMap::getStav, getSoord().getMoustred().toWgs(),
					spravaMap.getBeziciStahovani().map(StavMapy::getMapa).orElse(null), vyrez());
			final String text = rada == null ? RADA_BEZ_SPRAVY : rada.text;
			final List<String> radky = text == null ? new ArrayList<>() : zalom(text, fm, Math.max(200, getWidth() - 40));
			final List<String> odkazy = new ArrayList<>();
			final List<KatalogovaMapa> mapy = new ArrayList<>();
			if (rada != null && rada.odkaz != null) {
				odkazy.add(rada.odkaz);
				mapy.add(rada.mapa);
			}
			final boolean sousedni = rada != null && !rada.sousedni.isEmpty();
			if (sousedni) {
				for (final RadaOfflineMapy r : rada.sousedni) {
					odkazy.add(r.odkaz);
					mapy.add(r.mapa);
				}
			}
			int w = fmTucne.stringWidth(RadaOfflineMapy.NADPIS);
			if (sousedni) {
				w = Math.max(w, fm.stringWidth(RadaOfflineMapy.SOUSEDNI));
			}
			for (final String r : radky) {
				w = Math.max(w, fm.stringWidth(r));
			}
			for (final String odkaz : odkazy) {
				w = Math.max(w, fm.stringWidth(odkaz));
			}
			w += 16;
			final int h = fmTucne.getHeight() + (radky.size() + odkazy.size() + (sousedni ? 1 : 0)) * fm.getHeight() + 8;
			final int x = (getWidth() - w) / 2;
			final int y = 8;
			g2.setColor(new Color(255, 255, 255, 215));
			g2.fillRect(x, y, w, h);
			g2.setColor(Color.DARK_GRAY);
			int yy = y + 4 + fmTucne.getAscent();
			g2.setFont(tucne);
			g2.drawString(RadaOfflineMapy.NADPIS, x + 8, yy);
			g2.setFont(obycejne);
			yy += fmTucne.getDescent() + fm.getAscent() + fmTucne.getLeading();
			for (final String r : radky) {
				g2.drawString(r, x + 8, yy);
				yy += fm.getHeight();
			}
			for (int i = 0; i < odkazy.size(); i++) {
				if (sousedni && i == (rada.odkaz != null ? 1 : 0)) {
					g2.setColor(Color.DARK_GRAY);
					g2.drawString(RadaOfflineMapy.SOUSEDNI, x + 8, yy);
					yy += fm.getHeight();
				}
				final String odkaz = odkazy.get(i);
				g2.setColor(ODKAZ);
				g2.drawString(odkaz, x + 8, yy);
				final int sirka = fm.stringWidth(odkaz);
				g2.drawLine(x + 8, yy + 2, x + 8 + sirka, yy + 2);
				odkazyRady.add(new Rectangle(x + 4, yy - fm.getAscent(), sirka + 8, fm.getHeight()));
				mapyRady.add(mapy.get(i));
				yy += fm.getHeight();
			}
		} finally {
			g2.dispose();
		}
	}

	/** Zalomí text po slovech na řádky nejvýš dané šířky. */
	static List<String> zalom(final String text, final FontMetrics fm, final int sirka) {
		final List<String> radky = new ArrayList<>();
		final StringBuilder radek = new StringBuilder();
		for (final String slovo : text.split(" ")) {
			if (radek.length() > 0 && fm.stringWidth(radek + " " + slovo) > sirka) {
				radky.add(radek.toString());
				radek.setLength(0);
			}
			if (radek.length() > 0) {
				radek.append(' ');
			}
			radek.append(slovo);
		}
		if (radek.length() > 0) {
			radky.add(radek.toString());
		}
		return radky;
	}
}
