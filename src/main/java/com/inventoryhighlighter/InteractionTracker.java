package com.inventoryhighlighter;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
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
        private final int capGroup;

        private Mark(int itemId, int markTick, int capGroup)
        {
            this.itemId = itemId;
            this.markTick = markTick;
            this.capGroup = capGroup;
        }
    }

    private final Client client;
    private final Map<Long, Mark> marks = new HashMap<>();

    @Inject
    private InteractionTracker(Client client)
    {
        this.client = client;
    }

    private static long key(int componentId, int slotIndex)
    {
        return (((long) componentId) << 32) | (slotIndex & 0xffffffffL);
    }

    // Record a click against the tick it happened on. Eat/Drink keep only the latest mark per group: the game consumes
    // the last-clicked, so that is the one to highlight.
    void mark(int componentId, int slotIndex, int itemId, int capGroup)
    {
        if (capGroup != CAP_NONE)
        {
            marks.values().removeIf(m -> m.capGroup == capGroup);
        }

        marks.put(key(componentId, slotIndex), new Mark(itemId, client.getTickCount(), capGroup));
    }

    // Active only during the tick the item was clicked: the mark's tick must equal the current tick, so the outline can
    // never roll into a later tick. The itemId match stops a client-side swap (e.g. Instant Inventory) inheriting the
    // outline of the item it replaced.
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
        return m != null && m.itemId == widgetItem.getId() && m.markTick == client.getTickCount();
    }

    // Drop marks from earlier ticks. Visibility is already gated by isActive's tick check, so this only keeps the map
    // from growing; removing by tick (not a blanket clear) cannot wipe a mark placed earlier in the current tick.
    void expireStale()
    {
        if (marks.isEmpty())
        {
            return;
        }

        int currentTick = client.getTickCount();
        marks.values().removeIf(m -> m.markTick != currentTick);
    }

    void clear()
    {
        marks.clear();
    }
}
