package cz.geokuk.plugins.mapy.sprava;

import cz.geokuk.framework.Event0;

/** Stavy několika map nebo vrstev se změnily naráz (znovu přečtené z disku); stavy jsou v {@link SpravaMapModel}. Odpaluje se na EDT. */
public class StavyMapEvent extends Event0<SpravaMapModel> {
}
