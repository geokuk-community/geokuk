package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;
import cz.geokuk.plugins.mapy.sprava.SpravaMapEvent;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;
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
	/** Kde je v kachlovníku odkaz rady, null když se nekreslí. */
	private Rectangle odkazRady;
	private transient RadaOfflineMapy rada;

	public void inject(final SpravaMapModel spravaMap) {
		this.spravaMap = spravaMap;
	}

	public void onEvent(final SpravaMapEvent event) {
		if (odkazRady != null || rada != null && rada.mapa != null && rada.mapa.getId().equals(event.getMapa().getId())) {
			repaint();
		}
	}

	/** Klik na odkaz rady stáhne nabízenou mapu; vrací, jestli klik patřil odkazu. */
	public boolean klikNaRadu(final Point bod) {
		if (odkazRady == null || !odkazRady.contains(bod) || rada == null || rada.mapa == null || spravaMap == null) {
			return false;
		}
		spravaMap.stahni(rada.mapa);
		return true;
	}

	public boolean jeNadOdkazemRady(final Point bod) {
		return odkazRady != null && odkazRady.contains(bod);
	}

	static final String RADA_BEZ_SPRAVY = "Offline mapy najdete v bloku Mapy vpravo dole ve stavovém řádku.";

	/** Atribuci kreslí přes kachle vpravo dole, radu, kde vzít offline mapu, nahoře uprostřed. */
	@Override
	protected void paintChildren(final Graphics g) {
		super.paintChildren(g);
		if (getKachloType() == null || getKachloType().getAtribuce().isEmpty()) {
			return;
		}
		odkazRady = null;
		rada = null;
		if (jeRada()) {
			kresliRadu((Graphics2D) g);
		}
		final String text = getKachloType().isOffline() && getKachleModel() != null ? getKachleModel().getAtribuceOfflineMapy() : getKachloType().getAtribuce();
		kresliAtribuci(g, this, text, 0);
	}

	/** Atribuce vpravo dole; řádek 0 je u spodního okraje, další nad ním. */
	static void kresliAtribuci(final Graphics g, final Component c, final String text, final int radek) {
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setFont(c.getFont().deriveFont(Font.PLAIN, 11f));
			final FontMetrics fm = g2.getFontMetrics();
			final int w = fm.stringWidth(text) + 8;
			final int h = fm.getHeight() + 2;
			final int x = c.getWidth() - w;
			final int y = c.getHeight() - h * (radek + 1);
			g2.setColor(new Color(255, 255, 255, 190));
			g2.fillRect(x, y, w, h);
			g2.setColor(Color.DARK_GRAY);
			g2.drawString(text, x + 4, y + 1 + fm.getAscent());
		} finally {
			g2.dispose();
		}
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
			rada = spravaMap == null ? null : RadaOfflineMapy.pro(spravaMap.getKatalog(), spravaMap::getStav, getSoord().getMoustred().toWgs());
			final String text = rada == null ? RADA_BEZ_SPRAVY : rada.text;
			final List<String> radky = text == null ? new ArrayList<>() : zalom(text, fm, Math.max(200, getWidth() - 40));
			final String odkaz = rada == null ? null : rada.odkaz;
			int w = fmTucne.stringWidth(RadaOfflineMapy.NADPIS);
			for (final String r : radky) {
				w = Math.max(w, fm.stringWidth(r));
			}
			if (odkaz != null) {
				w = Math.max(w, fm.stringWidth(odkaz));
			}
			w += 16;
			final int h = fmTucne.getHeight() + (radky.size() + (odkaz != null ? 1 : 0)) * fm.getHeight() + 8;
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
			if (odkaz != null) {
				g2.setColor(ODKAZ);
				g2.drawString(odkaz, x + 8, yy);
				final int sirka = fm.stringWidth(odkaz);
				g2.drawLine(x + 8, yy + 2, x + 8 + sirka, yy + 2);
				odkazRady = new Rectangle(x + 4, yy - fm.getAscent() - 2, sirka + 8, fm.getHeight() + 4);
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
