package cz.geokuk.core.program.prototyp;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Ruční spuštění prototypu: {@code java -cp ... cz.geokuk.core.program.prototyp.PrototypStavovehoRadku [složka-se-snímky]}. Se složkou uloží snímky a skončí.
 */
public class PrototypStavovehoRadku {

	private static JPanel panel(final String... popisky) {
		final JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		p.setBorder(BorderFactory.createEtchedBorder());
		for (final String s : popisky) {
			p.add(new JLabel(s));
		}
		return p;
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
		final JPrepinaceZdroju[] prepinace = new JPrepinaceZdroju[1];
		final JVyletCombo[] combo = new JVyletCombo[1];
		final JDialog[] dialogy = new JDialog[1];
		final JButton[] nastaveniTlacitko = new JButton[1];
		SwingUtilities.invokeAndWait(() -> {
			okno[0] = new JFrame("Prototyp stavového řádku");
			okno[0].setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			final JPanel mapa = new JPanel();
			mapa.setBackground(new Color(0xDDE8D0));
			mapa.setPreferredSize(new Dimension(1180, 470));
			final JPanel radek = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			radek.add(panel("Myš:", "50°05'12.3\"N, 14°25'01.7\"E", "Z=14"));
			radek.add(panel("Vše:", "58211/54008", "Filtr:", "12880/12104"));
			final JButton nastaveni = new JButton("Nastavení…");
			nastaveni.addActionListener(e -> {
				final JDialog dialog = new JDialog(okno[0], "Nastavení (prototyp)", false);
				dialog.add(new JLabel("  Nastavení se otevře i během načítání zdrojů.  "));
				dialog.pack();
				dialog.setLocation(okno[0].getX() + 80, okno[0].getY() + 80);
				dialog.setVisible(true);
				dialogy[0] = dialog;
			});
			new javax.swing.Timer(400, e -> zdroje.posunNacitani(5)).start();
			prepinace[0] = new JPrepinaceZdroju(zdroje, () -> JOptionPane.showMessageDialog(okno[0], "Přehled zdrojů (prototyp)"));
			radek.add(prepinace[0]);
			combo[0] = new JVyletCombo(vylety);
			radek.add(combo[0]);
			radek.add(nastaveni);
			nastaveniTlacitko[0] = nastaveni;
			okno[0].add(mapa, BorderLayout.CENTER);
			okno[0].add(radek, BorderLayout.SOUTH);
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
		snimek(robot, okno[0], new File(slozka, "1-stavovy-radek.png"));
		SwingUtilities.invokeAndWait(() -> {
			nastaveniTlacitko[0].doClick();
			prepinace[0].ukazSeznam();
		});
		Thread.sleep(500);
		snimek(robot, okno[0], new File(slozka, "2-seznam-zdroju.png"));
		SwingUtilities.invokeAndWait(() -> {
			dialogy[0].dispose();
			prepinace[0].zavriSeznam();
			zdroje.setRezim(ZdrojeModel.Rezim.JEN_GPX);
		});
		Thread.sleep(300);
		snimek(robot, okno[0], new File(slozka, "3-jen-gpx.png"));
		SwingUtilities.invokeAndWait(() -> {
			zdroje.setRezim(ZdrojeModel.Rezim.VSE);
			combo[0].ukazMenu(combo[0].getComponent(1));
		});
		Thread.sleep(500);
		snimek(robot, okno[0], new File(slozka, "4-vylety.png"));
		System.exit(0);
	}

	private static void snimek(final Robot robot, final JFrame okno, final File soubor) throws Exception {
		final Rectangle r = okno.getBounds();
		final BufferedImage img = robot.createScreenCapture(new Rectangle(0, 0, Toolkit.getDefaultToolkit().getScreenSize().width, r.y + r.height + 10));
		ImageIO.write(img, "png", soubor);
	}
}
