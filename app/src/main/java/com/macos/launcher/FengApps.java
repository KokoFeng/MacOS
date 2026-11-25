package com.macos.launcher;

/** Routes app names to the native apps built into the launcher. */
public class FengApps {
    public static boolean open(MainActivity a, String name) {
        switch (name) {
            case "Feng Cut": a.openOrFocus("Feng Cut", () -> CutApp.open(a)); return true;
            case "Feng Design": a.openOrFocus("Feng Design", () -> DesignApp.open(a)); return true;
            case "Feng Photo": a.openOrFocus("Feng Photo", () -> PhotoApp.open(a)); return true;
            case "Feng Docs": a.openOrFocus("Feng Docs", () -> DocsApp.open(a)); return true;
            case "Feng Sheets": a.openOrFocus("Feng Sheets", () -> SheetsApp.open(a)); return true;
            case "Feng Code": a.openOrFocus("Feng Code", () -> CodeApp.open(a)); return true;
            case "2048": a.openOrFocus("2048", () -> Game2048.open(a)); return true;
            case "Blocks": a.openOrFocus("Blocks", () -> GameBlocks.open(a)); return true;
            case "Music": a.openOrFocus("Music", () -> MusicApp.open(a)); return true;
            default: return false;
        }
    }

    public static boolean isNative(String name) {
        for (String n : new String[]{"Feng Cut", "Feng Design", "Feng Photo", "Feng Docs", "Feng Sheets", "Feng Code", "2048", "Blocks", "Music"}) if (n.equals(name)) return true;
        return false;
    }
}
