package com.inventoryhighlighter;

import javax.inject.Singleton;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;

@Singleton
// Stores widget identity for the currently hovered item so duplicate item IDs in different slots stay distinct.
public class HoverState
{
    private int componentId = -1;
    private int slotIndex = -1;
    private int itemId = -1;

    public void setHoveredItem(int componentId, int slotIndex, int itemId)
    {
        this.componentId = componentId;
        this.slotIndex = slotIndex;
        this.itemId = itemId;
    }

    public boolean isItemHovered(WidgetItem item)
    {
        if (item == null || componentId == -1 || slotIndex == -1 || itemId == -1)
        {
            return false;
        }

        Widget widget = item.getWidget();
        return widget != null
            && widget.getId() == componentId
            && widget.getIndex() == slotIndex
            && item.getId() == itemId;
    }

    public void clear()
    {
        componentId = -1;
        slotIndex = -1;
        itemId = -1;
    }
}
