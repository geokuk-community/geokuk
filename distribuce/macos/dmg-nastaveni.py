# Okno dmg pro dmgbuild: GeoKuk.app vlevo, šipka na pozadí, odkaz na Aplikace vpravo, CTIMNE.txt dole.
# Souřadnice ikon odpovídají pozadí z grafika/generator/dmg-pozadi.py.
# Proměnné app, ctimne, pozadi a ikona předává sestav-dmg.sh přes dmgbuild -D.
import os.path

app = defines["app"]
ctimne = defines["ctimne"]

format = "ULMO"  # lzma, o čtvrtinu menší než zlib, otevře ho macOS 10.15 a novější
filesystem = "HFS+"
size = None

files = [app, ctimne]
symlinks = {"Aplikace": "/Applications"}
icon = defines["ikona"]
background = defines["pozadi"]

window_rect = ((200, 120), (640, 440))
default_view = "icon-view"
show_status_bar = False
show_tab_view = False
show_toolbar = False
show_pathbar = False
show_sidebar = False

arrange_by = None
icon_size = 96
text_size = 13
icon_locations = {
    os.path.basename(app): (160, 170),
    "Aplikace": (480, 170),
    os.path.basename(ctimne): (320, 370),
}
