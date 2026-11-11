package dev.sobrio.rebornquests;

import java.util.List;

import dev.sobrio.rebornquests.MessageTime.Entry;

/** Server side has no screen to open and nothing to draw, so only the client proxy reacts to the packets. */
public class CommonProxy {
    public void showBoard(BoardData data) {}

    public void showTimed(int mode, boolean replaceLog, List<Entry> entries) {}

    /** Only the client has a screen to draw the mission clock on. */
    public void registerHud() {}
}
