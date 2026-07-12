package com.inventoryhighlighter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;

@Singleton
public class InteractionTracker
{
    public static final int CAP_NONE = 0;
    public static final int CAP_EAT = 1;
    public static final int CAP_DRINK = 2;

    enum Reconcile
    {
        REFRESH,
        CLEAR
    }

    private static final class Mark
    {
        private final int itemId;
        private final int capGroup;
        private final boolean inventory;
        private int quantity;

        private Mark(int itemId, int quantity, int capGroup, boolean inventory)
        {
            this.itemId = itemId;
            this.quantity = quantity;
            this.capGroup = capGroup;
            this.inventory = inventory;
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

    // A changed quantity means an ongoing multi-tick action (keep highlighting); anything else clears at this reconcile.
    static Reconcile decide(int markedId, int markedQty, int currentId, int currentQty)
    {
        return currentId == markedId && currentQty != markedQty ? Reconcile.REFRESH : Reconcile.CLEAR;
    }

    // Eat/Drink keep only the latest mark per group: the game consumes the last-clicked, so highlight that one.
    void mark(int componentId, int slotIndex, int itemId, boolean inventory, int capGroup)
    {
        if (capGroup != CAP_NONE)
        {
            marks.values().removeIf(m -> m.capGroup == capGroup);
        }

        Item item = inventory ? inventoryItem(slotIndex) : null;
        int quantity = item == null ? 0 : item.getQuantity();
        marks.put(key(componentId, slotIndex), new Mark(itemId, quantity, capGroup, inventory));
    }

    void reconcile()
    {
        if (marks.isEmpty())
        {
            return;
        }

        Iterator<Map.Entry<Long, Mark>> it = marks.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<Long, Mark> entry = it.next();
            Mark m = entry.getValue();

            int currentId = m.itemId;
            int currentQty = m.quantity;
            if (m.inventory)
            {
                Item item = inventoryItem((int) (entry.getKey() & 0xffffffffL));
                currentId = item == null ? -1 : item.getId();
                currentQty = item == null ? 0 : item.getQuantity();
            }

            if (decide(m.itemId, m.quantity, currentId, currentQty) == Reconcile.CLEAR)
            {
                it.remove();
            }
            else
            {
                m.quantity = currentQty;
            }
        }
    }

    // itemId match so a client-side swap (e.g. Instant Inventory) can't inherit the outline of the item it replaced.
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
        return m != null && m.itemId == widgetItem.getId();
    }

    void clear()
    {
        marks.clear();
    }

    private Item inventoryItem(int slot)
    {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null)
        {
            return null;
        }

        Item[] items = inventory.getItems();
        return slot >= 0 && slot < items.length ? items[slot] : null;
    }
}
