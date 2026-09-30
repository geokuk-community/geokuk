package cz.geokuk.util.gui;

import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import cz.geokuk.core.program.JGeokukToolbar;
import cz.geokuk.framework.Factory;
import cz.geokuk.framework.ToggleAction0;

public abstract class MenuStrujce {

	protected final JMenuBar menuBar;
	protected JMenu menu;
	protected JMenuItem item;

	protected final JGeokukToolbar tb;

	protected Factory factory;

	public MenuStrujce(final JMenuBar menuBar, final JGeokukToolbar toolBar) {
		this.menuBar = menuBar;
		tb = toolBar;
	}

	public JMenuBar getMenuBar() {
		return menuBar;
	}

	public void inject(final Factory factory) {
		this.factory = factory;
	}

	protected void item(final Action action) {
		if (action instanceof ToggleAction0) {
			final ToggleAction0 moa = (ToggleAction0) action;
			item = new JCheckBoxMenuItem();
			moa.join(item);
			menu.add(item);
		} else {
			item = new JMenuItem(action);
			menu.add(item);
		}
	}

	protected void item(final ToggleAction0 action, final ButtonGroup bg) {
		item = new JRadioButtonMenuItem(action) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean processKeyBinding(final KeyStroke ks, final KeyEvent e, final int condition, final boolean pressed) {
				if (condition == WHEN_IN_FOCUSED_WINDOW && (ks.getModifiers() & ~(InputEvent.SHIFT_MASK | InputEvent.SHIFT_DOWN_MASK)) == 0 && pisePismeno()) {
					return false;
				}
				return super.processKeyBinding(ks, e, condition, pressed);
			}
		};
		action.join(item);
		menu.add(item);
		bg.add(item);
	}

	/** Písmeno napsané do textového pole nebo do otevřeného menu nesmí spustit zkratku bez modifikátoru nebo se Shiftem (třeba přepnout mapu). */
	static boolean pisePismeno() {
		return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextComponent
				|| MenuSelectionManager.defaultManager().getSelectedPath().length > 0;
	}

	protected abstract void makeMenu();

	protected void menu(final String name, final String aTooltip) {
		menu = new JMenu(name);
		menu.setToolTipText(aTooltip);
		menuBar.add(menu);
	}

	protected void separator() {
		menu.addSeparator();
	}
}
