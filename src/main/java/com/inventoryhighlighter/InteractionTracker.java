package com.inventoryhighlighter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Constants;
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

    // One game tick in client cycles, from the API tick lengths. The flash window is measured from the click cycle,
    // so a still-present item shows for a consistent ~1 tick regardless of when in the tick it was clicked.
    private static final int FLASH_CYCLES = Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

    enum Reconcile
    {
        REFRESH,
        KEEP,
        CLEAR
    }

    private static final class Mark
    {
        private final int itemId;
        private final int capGroup;
        private final boolean inventory;
        private int quantity;
        private int clickCycle;

        private Mark(int itemId, int quantity, int clickCycle, int capGroup, boolean inventory)
        {
            this.itemId = itemId;
            this.quantity = quantity;
            this.clickCycle = clickCycle;
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

    // Gone/replaced clears now; a changed quantity is an ongoing action (refresh the window); an unchanged item holds
    // until its window elapses.
    static Reconcile decide(int markedId, int markedQty, int clickCycle, int currentId, int currentQty, int currentCycle)
    {
        if (currentId != markedId)
        {
            return Reconcile.CLEAR;
        }
        if (currentQty != markedQty)
        {
            return Reconcile.REFRESH;
        }
        return currentCycle - clickCycle >= FLASH_CYCLES ? Reconcile.CLEAR : Reconcile.KEEP;
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
        marks.put(key(componentId, slotIndex), new Mark(itemId, quantity, client.getGameCycle(), capGroup, inventory));
    }

    void reconcile()
    {
        if (marks.isEmpty())
        {
            return;
        }

        int currentCycle = client.getGameCycle();
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

            switch (decide(m.itemId, m.quantity, m.clickCycle, currentId, currentQty, currentCycle))
            {
                case CLEAR:
                    it.remove();
                    break;
                case REFRESH:
                    m.quantity = currentQty;
                    m.clickCycle = currentCycle;
                    break;
                default:
                    break;
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
        return m != null && m.itemId == widgetItem.getId() && client.getGameCycle() - m.clickCycle < FLASH_CYCLES;
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
