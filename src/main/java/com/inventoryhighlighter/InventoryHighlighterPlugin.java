package com.inventoryhighlighter;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
    name = "Inventory Hover Highlighter",
    description = "Highlights specified items in your inventory",
    tags = {"inventory", "highlight", "items", "overlay", "tagging"}
)
// Coordinates plugin lifecycle and turns RuneLite menu-hover events into a specific hovered widget item.
public class InventoryHighlighterPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private InventoryHighlighterOverlay overlay;

    @Inject
    private HoverState hoverState;

    @Inject
    private InventoryHighlighterConfig config;

    @Inject
    private InteractionTracker interactionTracker;

    private String lastItemListValue = "";

    @Override
    protected void startUp()
    {
        log.debug("InventoryHighlighter started");
        lastItemListValue = config.itemList();
        hoverState.clear();
        interactionTracker.clear();
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        hoverState.clear();
        interactionTracker.clear();
        log.debug("InventoryHighlighter stopped");
    }

    @Provides
    InventoryHighlighterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(InventoryHighlighterConfig.class);
    }

    @Subscribe
    public void onPostMenuSort(PostMenuSort event)
    {
        // PostMenuSort is the hover-change signal while the right-click menu is closed; keep the last hover while it is open.
        if (client.isMenuOpen())
        {
            return;
        }

        MenuEntry[] menuEntries = client.getMenu().getMenuEntries();
        if (menuEntries.length == 0)
        {
            hoverState.clear();
            return;
        }

        updateHoveredItem(menuEntries[menuEntries.length - 1]);
    }

    private void updateHoveredItem(MenuEntry menuEntry)
    {
        // Menu entries identify the hovered widget slot, so duplicate items are distinguished by slot instead of item ID alone.
        Widget widget = menuEntry.getWidget();
        if (!isSupportedItemWidget(widget))
        {
            hoverState.clear();
            return;
        }

        int itemId = widget.getItemId();
        int slotIndex = widget.getIndex();
        if (slotIndex == -1)
        {
            slotIndex = menuEntry.getParam0();
        }

        if (itemId == -1 || slotIndex == -1 || !overlay.shouldHighlightItem(itemId))
        {
            hoverState.clear();
            return;
        }

        hoverState.setHoveredItem(widget.getId(), slotIndex, itemId);
    }

    private boolean isSupportedItemWidget(Widget widget)
    {
        // Limit hover tracking to inventory-like interfaces that WidgetItemOverlay also renders over.
        if (widget == null || widget.getItemId() == -1)
        {
            return false;
        }

        int componentId = widget.getId();
        int interfaceId = WidgetUtil.componentToInterface(componentId);
        return componentId == InterfaceID.Inventory.ITEMS
            || componentId == InterfaceID.Bankmain.ITEMS
            || componentId == InterfaceID.SharedBank.ITEMS
            || interfaceId == InterfaceID.INVENTORY
            || interfaceId == InterfaceID.BANKMAIN
            || interfaceId == InterfaceID.BANKSIDE
            || interfaceId == InterfaceID.SHARED_BANK
            || interfaceId == InterfaceID.SHARED_BANK_SIDE
            || interfaceId == InterfaceID.BANK_DEPOSITBOX
            || interfaceId == InterfaceID.SHOPSIDE
            || interfaceId == InterfaceID.GE_OFFERS_SIDE
            || interfaceId == InterfaceID.GE_PRICECHECKER_SIDE
            || interfaceId == InterfaceID.EQUIPMENT_SIDE
            || interfaceId == InterfaceID.SEED_VAULT_DEPOSIT
            || interfaceId == InterfaceID.TRADEMAIN
            || interfaceId == InterfaceID.TRADESIDE
            || interfaceId == InterfaceID.POH_COSTUMES_SIDE;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() != InventoryID.INV && event.getContainerId() != InventoryID.BANK)
        {
            return;
        }

        hoverState.clear();
        overlay.clearCache();
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        hoverState.clear();
        overlay.clearCache();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!InventoryHighlighterConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        String currentItemList = config.itemList();
        if (!currentItemList.equals(lastItemListValue))
        {
            lastItemListValue = currentItemList;
        }

        hoverState.clear();
        overlay.clearCache();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!config.showInteract() || !hoverState.isSet())
        {
            return;
        }

        String option = event.getMenuOption();
        if (option == null)
        {
            return;
        }

        option = Text.removeTags(option);
        if ("Cancel".equalsIgnoreCase(option))
        {
            return;
        }

        int capGroup = "Eat".equalsIgnoreCase(option) ? InteractionTracker.CAP_EAT
            : "Drink".equalsIgnoreCase(option) ? InteractionTracker.CAP_DRINK
            : InteractionTracker.CAP_NONE;

        int component = hoverState.getComponentId();
        boolean inventory = component == InterfaceID.Inventory.ITEMS
            || WidgetUtil.componentToInterface(component) == InterfaceID.INVENTORY;

        interactionTracker.mark(component, hoverState.getSlotIndex(), hoverState.getItemId(), inventory, capGroup);
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        interactionTracker.reconcile();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            interactionTracker.clear();
        }
    }
}
