package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Window;

import javax.swing.JDialog;
import javax.swing.WindowConstants;

import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze;

/** Modální dialog Optimalizace cache dlaždic; za běhu jde zavřít jen tlačítkem Zrušit. */
public class DialogOptimalizace extends JDialog {

	private static final long serialVersionUID = 1L;

	private final PanelOptimalizace panel;

	private DialogOptimalizace(final Window vlastnik, final OptimalizaceDatabaze optimalizace) {
		super(vlastnik, OptimalizaceTexty.NAZEV, ModalityType.APPLICATION_MODAL);
		panel = new PanelOptimalizace(optimalizace, this::dispose);
		setContentPane(panel);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(final java.awt.event.WindowEvent e) {
				if (!panel.bezi()) {
					dispose();
				}
			}
		});
		pack();
		setLocationRelativeTo(vlastnik);
	}

	/** Zobrazí dialog; s {@code hned} rovnou spustí optimalizaci, když je možná. */
	public static void zobraz(final Window vlastnik, final OptimalizaceDatabaze optimalizace, final boolean hned) {
		final DialogOptimalizace d = new DialogOptimalizace(vlastnik, optimalizace);
		if (hned && d.panel.faze() == PanelOptimalizace.Faze.PRIPRAVENO) {
			javax.swing.SwingUtilities.invokeLater(d.panel::spust);
		}
		d.setVisible(true);
	}
}
