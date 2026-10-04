package cz.geokuk.util.gui;

import java.awt.*;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;

import cz.geokuk.framework.Action0;

public class JIconCheckBox extends JCheckBox {
	private abstract class Icon0 implements Icon {
		@Override
		public int getIconHeight() {
			return icon.getIconHeight();
		}

		@Override
		public int getIconWidth() {
			return icon.getIconWidth();
		}
	}

	private class RolloverIcon extends Icon0 {

		@Override
		public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
			icon.paintIcon(c, g, x, y);
			g.setColor(Color.BLACK);
			final int iconWidth = icon.getIconWidth();
			final int iconHeight = icon.getIconHeight();
			g.drawRect(x - 4, y - 4, iconWidth - 1 + 8, iconHeight - 1 + 8);
			g.setColor(Color.RED);
		}

	}

	private class RollOverSelectedIcon extends Icon0 {
		@Override
		public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
			new SelectedIcon().paintIcon(c, g, x, y);
			new RolloverIcon().paintIcon(c, g, x, y);
		}

	}

	private class SelectedIcon extends Icon0 {
		@Override
		public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
			g.setColor(Color.GREEN);
			g.fillRect(x - 2, y - 2, icon.getIconWidth() + 4, icon.getIconHeight() + 4);
			icon.paintIcon(c, g, x, y);
		}

	}

	private static final long serialVersionUID = 1L;

	private Icon icon;

	private int maxVyskaIkony;

	public JIconCheckBox() {}

	public JIconCheckBox(final Action0 action) {
		super(action);
		setIcon(action.getIcon());
	}

	public JIconCheckBox(final Icon icon) {
		super(icon);
	}

	/** Vyšší ikony se zmenší na tuto výšku, 0 = bez omezení. */
	public void setMaxVyskaIkony(final int maxVyskaIkony) {
		this.maxVyskaIkony = maxVyskaIkony;
	}

	@Override
	public void setIcon(final Icon defaultIcon) {
		final Icon ikona = zmensi(defaultIcon, maxVyskaIkony);
		super.setIcon(ikona);
		icon = ikona;
		setRolloverIcon(new RolloverIcon());
		setSelectedIcon(new SelectedIcon());
		setRolloverSelectedIcon(new RollOverSelectedIcon());
	}


	static Icon zmensi(final Icon ikona, final int maxVyska) {
		if (ikona == null || maxVyska <= 0 || ikona.getIconHeight() <= maxVyska) {
			return ikona;
		}
		final BufferedImage obr = new BufferedImage(ikona.getIconWidth(), ikona.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = obr.createGraphics();
		ikona.paintIcon(null, g, 0, 0);
		g.dispose();
		final int sirka = Math.max(1, ikona.getIconWidth() * maxVyska / ikona.getIconHeight());
		return new ImageIcon(obr.getScaledInstance(sirka, maxVyska, Image.SCALE_SMOOTH));
	}
}
