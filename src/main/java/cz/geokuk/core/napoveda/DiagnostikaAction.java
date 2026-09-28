package cz.geokuk.core.napoveda;

import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;

import javax.swing.*;

import cz.geokuk.framework.Action0;

public class DiagnostikaAction extends Action0 {

	private static final long serialVersionUID = 1L;

	public DiagnostikaAction() {
		super("Informace pro hlášení chyby ...");
		putValue(SHORT_DESCRIPTION, "Zobrazí verzi programu, údaje o prostředí a poslední události, které lze zkopírovat do hlášení chyby.");
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		final String text = Diagnostika.text();
		final JTextArea area = new JTextArea(text, 20, 70);
		area.setEditable(false);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
		area.setCaretPosition(0);
		final Object[] options = { "Kopírovat do schránky", "Nahlásit na GitHubu", "Zavřít" };
		final int n = JOptionPane.showOptionDialog(getMainFrame(), new JScrollPane(area), "Informace pro hlášení chyby", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
		if (n == 0) {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		} else if (n == 1) {
			ZadatProblemAction.otevri(text);
		}
	}
}
