package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Manažer cache pro testy: upozornění uživateli jen zaznamená, při běhu testů s displejem by jinak vyskočilo okno. */
class TichyKachleDBManager extends KachleDBManager {

	final List<String> zaznamenanaUpozorneni = new CopyOnWriteArrayList<>();

	TichyKachleDBManager(final KachleCacheFolderHolder folderHolder) {
		super(folderHolder);
	}

	@Override
	void upozorni(final String text) {
		zaznamenanaUpozorneni.add(text);
	}
}
