package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.swing.SwingUtilities;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.napoveda.CoJeNoveho;
import cz.geokuk.core.napoveda.JCoJeNoveho;
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
	/** Rada a atribuce, jak se naposledy kreslily nebo zkontrolovaly; null, když se nekreslí. */
	private transient RozlozeniRady posledniRada;
	private transient AtribuceMapy.Rozlozeni posledniAtribuce;

	public void inject(final SpravaMapModel spravaMap) {
		this.spravaMap = spravaMap;
	}

	public void onEvent(final SpravaMapEvent event) {
		if (!odkazyRady.isEmpty() || rada != null && rada.mapa != null && rada.mapa.getId().equals(event.getMapa().getId())) {
			repaint();
		}
	}

	public void onEvent(final cz.geokuk.plugins.mapy.sprava.SpravaVrstevEvent event) {
		prekresliRaduAAtribuci();
	}

	public void onEvent(final cz.geokuk.plugins.mapy.sprava.KatalogVrstevEvent event) {
		prekresliRaduAAtribuci();
	}

	public void onEvent(final cz.geokuk.plugins.mapy.sprava.StavyMapEvent event) {
		prekresliRaduAAtribuci();
	}

	/** Překreslí rozhraní rady a atribuce, jen když se jejich obsah od posledního kreslení změnil; mapa pod nimi se nepřekresluje. */
	private void prekresliRaduAAtribuci() {
		final RozlozeniRady novaRada = maAtribuci() && jeRada() ? sestavRadu(getFontMetrics(getFont().deriveFont(Font.BOLD, 12f)), getFontMetrics(getFont().deriveFont(Font.PLAIN, 12f))) : null;
		if (!Objects.equals(podpis(novaRada), podpis(posledniRada))) {
			prekresli(sjednot(posledniRada == null ? null : posledniRada.oblast(), novaRada == null ? null : novaRada.oblast()));
		}
		posledniRada = novaRada;
		final AtribuceMapy.Rozlozeni novaAtribuce = rozlozAtribuci(getFontMetrics(getFont().deriveFont(Font.PLAIN, 10f)));
		if (!Objects.equals(podpis(novaAtribuce), podpis(posledniAtribuce))) {
			prekresli(sjednot(posledniAtribuce == null ? null : posledniAtribuce.pole, novaAtribuce == null ? null : novaAtribuce.pole));
		}
		posledniAtribuce = novaAtribuce;
	}

	private void prekresli(final Rectangle oblast) {
		if (oblast != null) {
			repaint(oblast.x - 2, oblast.y - 2, oblast.width + 4, oblast.height + 4);
		}
	}

	private static Rectangle sjednot(final Rectangle a, final Rectangle b) {
		return a == null ? b : b == null ? a : a.union(b);
	}

	private static List<Object> podpis(final RozlozeniRady r) {
		return r == null ? null : Arrays.asList(r.oblast(), r.radky, r.odkazy);
	}

	private static List<Object> podpis(final AtribuceMapy.Rozlozeni r) {
		return r == null ? null : Arrays.asList(r.text, r.pole);
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

	/** Radu, kde vzít offline mapu, kreslí přes kachle nahoře uprostřed. */
	@Override
	protected void paintChildren(final Graphics g) {
		super.paintChildren(g);
		odkazyRady.clear();
		mapyRady.clear();
		rada = null;
		posledniRada = null;
		if (maAtribuci() && jeRada()) {
			kresliRadu((Graphics2D) g);
		}
	}

	private boolean maAtribuci() {
		return getKachloType() != null && !getKachloType().getAtribuce().isEmpty();
	}

	/**
	 * Atribuce: jeden zkrácený řádek vpravo dole v pravé čtvrtině šířky, prostředek dole patří měřítku; plné znění po kliku na ikonu ⓘ. Kreslí ji vrstva {@link JOdkazRadySlide} nad
	 * všemi ostatními vrstvami mapy, aby ji nezakryly.
	 */
	void kresliAtribuci(final Graphics2D g) {
		ikonaAtribuce = null;
		posledniAtribuce = null;
		if (!maAtribuci()) {
			return;
		}
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setFont(getFont().deriveFont(Font.PLAIN, 10f));
			final FontMetrics fm = g2.getFontMetrics();
			final AtribuceMapy.Rozlozeni r = rozlozAtribuci(fm);
			AtribuceMapy.kresli(g2, r, fm);
			ikonaAtribuce = r.ikona;
			posledniAtribuce = r;
		} finally {
			g2.dispose();
		}
	}

	private AtribuceMapy.Rozlozeni rozlozAtribuci(final FontMetrics fm) {
		return maAtribuci() ? AtribuceMapy.rozloz(zkratkyAtribuce(), fm, getWidth(), getHeight(), getWidth() / 4) : null;
	}

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
		if (getKachloType() != null && getKachloType().isOffline() && getKachleModel() != null && getKachleModel().getTemaOfflineMapy().getAtribuce() != null) {
			v.add(getKachleModel().getTemaOfflineMapy().getAtribuce());
		}
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

	/** Ukáže plné znění atribuce v okně jako Co je nového. */
	void ukazPlnouAtribuci() {
		final JCoJeNoveho obsah = new JCoJeNoveho();
		obsah.setHtml(htmlAtribuce(plnaAtribuce()));
		JCoJeNoveho.dialog(SwingUtilities.getWindowAncestor(this), "Atribuce mapy", obsah).setVisible(true);
	}

	/** Plné znění jako HTML; texty atribuce přicházejí i ze staženého katalogu, proto se escapují. */
	static String htmlAtribuce(final List<String> texty) {
		final StringBuilder html = new StringBuilder("<html><body>");
		for (final String t : texty) {
			if (!t.trim().isEmpty()) {
				html.append("<p>").append(CoJeNoveho.escape(t)).append("</p>");
			}
		}
		return html.append("<p><a href=\"https://www.openstreetmap.org/copyright\">Autorská práva OpenStreetMap</a></p></body></html>").toString();
	}

	/** Offline mapa a střed výřezu nepokrývá žádná podrobná mapa. */
	boolean jeRada() {
		return getKachloType() != null && getKachloType().isOffline() && getKachleModel() != null && isSoordInitialized() && getSoord() != null
				&& !getKachleModel().jePodrobnaOfflineMapa(getSoord().getMoustred().toWgs(), getSoord().getMoumer());
	}

	/** Rada rozložená do kachlovníku: text, odkazy a obdélník, který zabírá. */
	private static final class RozlozeniRady {
		final RadaOfflineMapy rada;
		final List<String> radky;
		final List<String> odkazy = new ArrayList<>();
		final List<KatalogovaMapa> mapy = new ArrayList<>();
		final boolean sousedni;
		final int x;
		final int y;
		final int w;
		final int h;

		RozlozeniRady(final RadaOfflineMapy rada, final List<String> radky, final boolean sousedni, final int x, final int y, final int w, final int h) {
			this.rada = rada;
			this.radky = radky;
			this.sousedni = sousedni;
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}

		Rectangle oblast() {
			return new Rectangle(x, y, w, h);
		}
	}

	private RozlozeniRady sestavRadu(final FontMetrics fmTucne, final FontMetrics fm) {
		final RadaOfflineMapy novaRada = spravaMap == null ? null : RadaOfflineMapy.pro(spravaMap.getKatalog(), spravaMap::getStav, getSoord().getMoustred().toWgs(),
				spravaMap.getBeziciStahovani().map(StavMapy::getMapa).orElse(null), vyrez());
		final String text = novaRada == null ? RADA_BEZ_SPRAVY : novaRada.text;
		final List<String> radky = text == null ? new ArrayList<>() : zalom(text, fm, Math.max(200, getWidth() - 40));
		final List<String> odkazy = new ArrayList<>();
		final List<KatalogovaMapa> mapy = new ArrayList<>();
		if (novaRada != null && novaRada.odkaz != null) {
			odkazy.add(novaRada.odkaz);
			mapy.add(novaRada.mapa);
		}
		final boolean sousedni = novaRada != null && !novaRada.sousedni.isEmpty();
		if (sousedni) {
			for (final RadaOfflineMapy r : novaRada.sousedni) {
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
		final RozlozeniRady r = new RozlozeniRady(novaRada, radky, sousedni, (getWidth() - w) / 2, 8, w, h);
		r.odkazy.addAll(odkazy);
		r.mapy.addAll(mapy);
		return r;
	}

	private void kresliRadu(final Graphics2D g) {
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			final Font tucne = getFont().deriveFont(Font.BOLD, 12f);
			final Font obycejne = getFont().deriveFont(Font.PLAIN, 12f);
			final FontMetrics fmTucne = g2.getFontMetrics(tucne);
			final FontMetrics fm = g2.getFontMetrics(obycejne);
			final RozlozeniRady rr = sestavRadu(fmTucne, fm);
			posledniRada = rr;
			rada = rr.rada;
			final List<String> radky = rr.radky;
			final List<String> odkazy = rr.odkazy;
			final List<KatalogovaMapa> mapy = rr.mapy;
			final boolean sousedni = rr.sousedni;
			final int x = rr.x;
			final int y = rr.y;
			final int w = rr.w;
			final int h = rr.h;
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
