package cz.geokuk.core.napoveda;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;

import javax.swing.*;

import cz.geokuk.framework.Action0;
import cz.geokuk.framework.JMyDialog0;

public class DiagnostikaAction extends Action0 {

	private static final long serialVersionUID = 1L;
	static final String VYNULOVAT = "Vynulovat měření";

	public DiagnostikaAction() {
		super("Informace pro hlášení chyby...");
		putValue(SHORT_DESCRIPTION, "Zobrazí verzi programu, údaje o prostředí a poslední události, které lze zkopírovat do hlášení chyby.");
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		ukaz(getMainFrame());
	}

	/** Nejmenší velikost okna a největší výchozí velikost; ve výchozím stavu okno nepřesáhne 90 % pracovní plochy. */
	static final Dimension NEJMENSI = new Dimension(480, 320);
	static final Dimension VYCHOZI = new Dimension(900, 650);

	/** Výchozí velikost okna pro danou pracovní plochu obrazovky. */
	static Dimension vychoziVelikost(final Rectangle plocha) {
		return new Dimension(Math.min(VYCHOZI.width, plocha.width * 9 / 10), Math.min(VYCHOZI.height, plocha.height * 9 / 10));
	}

	/** Ukáže informace pro hlášení chyby; na GitHub se odešlou až tlačítkem Nahlásit na GitHubu. */
	static void ukaz(final java.awt.Component rodic) {
		sestav(rodic).setVisible(true);
	}

	/** Okno s měnitelnou velikostí: text se roztahuje s oknem, tlačítka zůstávají dole. */
	static JDialog sestav(final java.awt.Component rodic) {
		final Window vlastnik = rodic instanceof Window ? (Window) rodic : rodic == null ? null : SwingUtilities.getWindowAncestor(rodic);
		final JDialog dialog = new JDialog(vlastnik, "Informace pro hlášení chyby", Dialog.ModalityType.APPLICATION_MODAL);
		final JTextArea area = new JTextArea(Diagnostika.text());
		area.setEditable(false);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
		area.setCaretPosition(0);
		final JScrollPane posuvnik = new JScrollPane(area);
		posuvnik.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

		final JButton kopirovat = new JButton("Kopírovat do schránky");
		kopirovat.addActionListener(ev -> {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(area.getText()), null);
			dialog.dispose();
		});
		final JButton nahlasit = new JButton("Nahlásit na GitHubu");
		nahlasit.addActionListener(ev -> {
			dialog.dispose();
			ZadatProblemAction.otevri();
		});
		final JButton vynulovat = new JButton(VYNULOVAT);
		vynulovat.setToolTipText("Začne měřit výkon kreslení znovu od teď, například před zopakováním pomalého postupu.");
		vynulovat.addActionListener(ev -> {
			Vykon.vynuluj();
			Diagnostika.zaznamenej("Měření výkonu vynulováno");
			area.setText(Diagnostika.text());
			area.setCaretPosition(0);
		});
		final JButton zavrit = new JButton("Zavřít");
		zavrit.addActionListener(ev -> dialog.dispose());
		final JPanel tlacitka = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		tlacitka.add(kopirovat);
		tlacitka.add(nahlasit);
		tlacitka.add(vynulovat);
		tlacitka.add(zavrit);

		dialog.getContentPane().add(posuvnik, BorderLayout.CENTER);
		dialog.getContentPane().add(tlacitka, BorderLayout.SOUTH);
		dialog.getRootPane().setDefaultButton(kopirovat);
		dialog.getRootPane().registerKeyboardAction(ev -> dialog.dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
		dialog.setResizable(true);
		dialog.pack();
		final Rectangle plocha = JMyDialog0.pracovniPlocha(vlastnik != null ? vlastnik.getGraphicsConfiguration() : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration());
		final Dimension nejmensi = new Dimension(Math.min(NEJMENSI.width, plocha.width), Math.min(NEJMENSI.height, plocha.height));
		dialog.setMinimumSize(nejmensi);
		dialog.setSize(vychoziVelikost(plocha));
		dialog.setLocationRelativeTo(vlastnik);
		return dialog;
	}
}
