package com.inventoryhighlighter;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;

@Singleton
public class InteractionTracker
{
    public static final int CAP_NONE = 0;
    public static final int CAP_EAT = 1;
    public static final int CAP_DRINK = 2;

    private static final class Mark
    {
        private final int itemId;
        private final int markTick;
        private final int markCycle;
        private final int capGroup;

        private Mark(int itemId, int markTick, int markCycle, int capGroup)
        {
            this.itemId = itemId;
            this.markTick = markTick;
            this.markCycle = markCycle;
            this.capGroup = capGroup;
        }
    }

    private final Client client;
    private final InventoryHighlighterConfig config;
    private final Map<Long, Mark> marks = new HashMap<>();

    @Inject
    private InteractionTracker(Client client, InventoryHighlighterConfig config)
    {
        this.client = client;
        this.config = config;
    }

    private static long key(int componentId, int slotIndex)
    {
        return (((long) componentId) << 32) | (slotIndex & 0xffffffffL);
    }

    // Min display time in client ticks (~20ms each).
    private int rolloverCycles()
    {
        return Math.round(config.interactRolloverMs() / (float) Constants.CLIENT_TICK_LENGTH);
    }

    // Eat/Drink keep only the latest mark per group; the game consumes the last-clicked.
    void mark(int componentId, int slotIndex, int itemId, int capGroup)
    {
        if (capGroup != CAP_NONE)
        {
            marks.values().removeIf(m -> m.capGroup == capGroup);
        }

        marks.put(key(componentId, slotIndex), new Mark(itemId, client.getTickCount(), client.getGameCycle(), capGroup));
    }

    // Lit during the click tick, then into the next tick until the min display time elapses. The itemId match stops a
    // client-side swap (e.g. Instant Inventory) inheriting the outline it replaced.
    boolean isActive(WidgetItem widgetItem)
    {
        if (widgetItem == null || marks.isEmpty())
        {
            return false;
        }

        Widget widget = widgetItem.getWidget();
        if (widget == null)
        {
            return false;
        }

        Mark m = marks.get(key(widget.getId(), widget.getIndex()));
        if (m == null || m.itemId != widgetItem.getId())
        {
            return false;
        }

        int currentTick = client.getTickCount();
        if (m.markTick == currentTick)
        {
            return true;
        }

        int rollover = rolloverCycles();
        return rollover > 0
            && currentTick == m.markTick + 1
            && client.getGameCycle() - m.markCycle < rollover;
    }

    // Drop marks past their window (one extra tick when a min display time is set).
    void expireStale()
    {
        if (marks.isEmpty())
        {
            return;
        }

        int currentTick = client.getTickCount();
        int maxAge = rolloverCycles() > 0 ? 1 : 0;
        marks.values().removeIf(m -> currentTick - m.markTick > maxAge);
    }

    void clear()
    {
        marks.clear();
    }
}
