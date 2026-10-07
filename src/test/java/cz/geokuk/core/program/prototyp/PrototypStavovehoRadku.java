package cz.geokuk.core.program.prototyp;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.*;

import cz.geokuk.core.program.prototyp.ZdrojeModel.Stav;
import cz.geokuk.core.program.prototyp.ZdrojeModel.Typ;

/**
 * Ruční spuštění prototypu: {@code java -cp ... cz.geokuk.core.program.prototyp.PrototypStavovehoRadku [složka-se-snímky]}. Se složkou uloží snímky a skončí.
 */
public class PrototypStavovehoRadku {

	/** Panel s pevnou šířkou podle nejširšího obsahu, aby se při změně textů nic neposouvalo. */
	private static JPanel panel(final String... prototypy) {
		final JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		p.setBorder(BorderFactory.createEtchedBorder());
		for (final String s : prototypy) {
			final JLabel l = new JLabel(s);
			l.setPreferredSize(new Dimension(l.getFontMetrics(l.getFont()).stringWidth(s) + 2, l.getPreferredSize().height));
			p.add(l);
		}
		return p;
	}

	private static JLabel pole(final String text, final String prototyp) {
		final JLabel l = new JLabel(text);
		l.setPreferredSize(new Dimension(l.getFontMetrics(l.getFont()).stringWidth(prototyp) + 2, l.getPreferredSize().height));
		return l;
	}

	/** Řádek: vlevo zbytek jako dnes, vpravo pevné bloky Výlet a Zdroje; ve verzi 6.4.0 je u výletu rozbalovač pojmenovaného výletu. */
	private static JPanel sestavListu(final ZdrojeModel zdroje, final VyletyModel vylety, final boolean verze64, final JPrepinaceZdroju[] prepinace, final JVyletCombo[] combo) {
		final JPanel vlevo = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		vlevo.add(panel("Myš:", "50°05'12.3\"N, 14°25'01.7\"E", "Z=14"));
		vlevo.add(panel("Vše:", "58211/54008", "Filtr:", "12880/12104"));
		final JPanel vpravo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
		final JPanel vylet;
		if (verze64) {
			combo[0] = new JVyletCombo(vylety);
			vylet = combo[0];
		} else {
			vylet = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
			vylet.setBorder(BorderFactory.createEtchedBorder());
			vylet.add(new JLabel("Výlet:"));
			vylet.add(pole(String.valueOf(vylety.getLovim()), "9999"));
			vylet.add(new JLabel("/"));
			vylet.add(pole(String.valueOf(vylety.getIgnoruji()), "9999"));
		}
		vpravo.add(vylet);
		prepinace[0] = new JPrepinaceZdroju(zdroje);
		vpravo.add(prepinace[0]);
		final JPanel lista = new JPanel(new BorderLayout());
		lista.add(vlevo, BorderLayout.WEST);
		lista.add(vpravo, BorderLayout.EAST);
		return lista;
	}

	public static void main(final String[] args) throws Exception {
		final ZdrojeModel zdroje = ZdrojeModel.ukazka();
		final VyletyModel vylety = new VyletyModel();
		vylety.nastavPocty(VyletyModel.VYCHOZI, 4, 1);
		vylety.novy("Šumava 2026");
		vylety.nastavPocty("Šumava 2026", 12, 3);
		vylety.novy("Víkend u Brna");
		vylety.nastavPocty("Víkend u Brna", 7, 0);
		vylety.aktivuj("Šumava 2026");

		final JFrame[] okno = new JFrame[1];
		final JPanel[] lista = new JPanel[2];
		final JPrepinaceZdroju[] prepinace = new JPrepinaceZdroju[1];
		final JVyletCombo[] combo = new JVyletCombo[1];
		final JDialog[] dialogy = new JDialog[1];
		final JPrepinaceZdroju[] zahozene = new JPrepinaceZdroju[1];
		SwingUtilities.invokeAndWait(() -> {
			okno[0] = new JFrame("Prototyp stavového řádku");
			okno[0].setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			final JPanel mapa = new JPanel();
			mapa.setBackground(new Color(0xDDE8D0));
			mapa.setPreferredSize(new Dimension(1280, 470));
			lista[1] = sestavListu(zdroje, vylety, true, zahozene, combo);
			lista[0] = sestavListu(zdroje, vylety, false, prepinace, new JVyletCombo[1]);
			if (args.length == 0) {
				new javax.swing.Timer(400, e -> zdroje.posunNacitani(5)).start();
			}
			okno[0].add(mapa, BorderLayout.CENTER);
			okno[0].add(lista[0], BorderLayout.SOUTH);
			okno[0].pack();
			okno[0].setLocation(20, 20);
			okno[0].setVisible(true);
		});
		if (args.length == 0) {
			return;
		}
		final File slozka = new File(args[0]);
		slozka.mkdirs();
		final Robot robot = new Robot();
		Thread.sleep(800);
		snimek(robot, okno[0], new File(slozka, "1-okno-6.3.0.png"));
		stavyListy(zdroje, lista[0], new File(slozka, "2-lista-stavy-6.3.0.png"));
		SwingUtilities.invokeAndWait(() -> prepinace[0].ukazSouhrn());
		Thread.sleep(500);
		snimek(robot, okno[0], new File(slozka, "3-uplna-tabulka.png"));
		SwingUtilities.invokeAndWait(() -> {
			prepinace[0].zavriSeznam();
			prepinace[0].ukazTyp(Typ.GPX);
		});
		Thread.sleep(500);
		snimek(robot, okno[0], new File(slozka, "4-detail-gpx.png"));
		SwingUtilities.invokeAndWait(() -> {
			prepinace[0].zavriSeznam();
			final JDialog prehled = new JDialog(okno[0], "Přehled zdrojů", false);
			prehled.add(new JZdrojePopup(zdroje, null));
			prehled.pack();
			prehled.setLocation(okno[0].getX() + 200, okno[0].getY() + 40);
			prehled.setVisible(true);
			dialogy[0] = prehled;
		});
		Thread.sleep(500);
		snimek(robot, okno[0], new File(slozka, "5-prehled-zdroju-okno.png"));
		SwingUtilities.invokeAndWait(() -> {
			dialogy[0].dispose();
			okno[0].remove(lista[0]);
			okno[0].add(lista[1], BorderLayout.SOUTH);
			okno[0].validate();
		});
		stavyListy(zdroje, lista[1], new File(slozka, "6-lista-stavy-6.4.0.png"));
		SwingUtilities.invokeAndWait(() -> combo[0].ukazMenu(combo[0].getComponent(1)));
		Thread.sleep(500);
		snimek(robot, okno[0], new File(slozka, "7-okno-6.4.0-vylety.png"));
		System.exit(0);
	}

	/** Lišta v různých stavech pod sebou; pozice prvků musí být ve všech řádcích stejné. */
	private static void stavyListy(final ZdrojeModel zdroje, final JPanel lista, final File soubor) throws Exception {
		final Object[][] scenare = {
				{ "Načteno, GSAK vypnut", new Stav[] { Stav.NACTENO, Stav.NACTENO, Stav.VYPNUTO, Stav.NACTENO } },
				{ "GeoGet se načítá", new Stav[] { Stav.NACTENO, Stav.NACITA_SE, Stav.NACTENO, Stav.NACTENO } },
				{ "GSAK zamčený", new Stav[] { Stav.NACTENO, Stav.NACTENO, Stav.ZAMCENO, Stav.NACTENO } },
				{ "OpenSAK chyba, GPX vypnut", new Stav[] { Stav.VYPNUTO, Stav.NACTENO, Stav.NACTENO, Stav.CHYBA } },
				{ "Vše vypnuto", new Stav[] { Stav.VYPNUTO, Stav.VYPNUTO, Stav.VYPNUTO, Stav.VYPNUTO } } };
		final List<BufferedImage> obrazky = new ArrayList<>();
		for (final Object[] scenar : scenare) {
			final Stav[] stavy = (Stav[]) scenar[1];
			final BufferedImage[] img = new BufferedImage[1];
			SwingUtilities.invokeAndWait(() -> {
				for (final Typ typ : Typ.values()) {
					zdroje.setStavTypu(typ, stavy[typ.ordinal()]);
				}
				final Dimension d = lista.getSize();
				img[0] = new BufferedImage(d.width, d.height + 16, BufferedImage.TYPE_INT_RGB);
				final Graphics2D g = img[0].createGraphics();
				g.setColor(Color.WHITE);
				g.fillRect(0, 0, d.width, d.height + 16);
				g.setColor(Color.BLACK);
				g.drawString((String) scenar[0], 6, 12);
				g.translate(0, 16);
				lista.paint(g);
				g.dispose();
			});
			obrazky.add(img[0]);
		}
		int vyska = 0;
		for (final BufferedImage i : obrazky) {
			vyska += i.getHeight();
		}
		final BufferedImage vysledek = new BufferedImage(obrazky.get(0).getWidth(), vyska, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = vysledek.createGraphics();
		int y = 0;
		for (final BufferedImage i : obrazky) {
			g.drawImage(i, 0, y, null);
			y += i.getHeight();
		}
		g.dispose();
		ImageIO.write(vysledek, "png", soubor);
		SwingUtilities.invokeAndWait(() -> {
			for (final Typ typ : Typ.values()) {
				zdroje.setStavTypu(typ, typ == Typ.GSAK ? Stav.ZAMCENO : Stav.NACTENO);
			}
		});
	}

	private static void snimek(final Robot robot, final JFrame okno, final File soubor) throws Exception {
		final Rectangle r = okno.getBounds();
		final BufferedImage img = robot.createScreenCapture(new Rectangle(0, 0, Toolkit.getDefaultToolkit().getScreenSize().width, r.y + r.height + 10));
		ImageIO.write(img, "png", soubor);
	}
}
