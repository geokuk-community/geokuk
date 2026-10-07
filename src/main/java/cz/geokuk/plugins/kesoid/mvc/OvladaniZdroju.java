package cz.geokuk.plugins.kesoid.mvc;

import java.io.File;

import cz.geokuk.plugins.kesoid.importek.TypZdroje;

/** Co přepínače zdrojů mění; nic z toho nečeká na načítání. */
public interface OvladaniZdroju {

	void setNacitatTyp(TypZdroje typ, boolean nacitat);

	void setNacitatVseVTypu(TypZdroje typ, boolean nacitat);

	void setNacitatVse(boolean nacitat);

	void setNacitatPolozku(File soubor, boolean nacitat);
}
