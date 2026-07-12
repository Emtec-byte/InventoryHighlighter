package com.inventoryhighlighter;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
// Renders the highlighted item and caches expensive item-name and filled-sprite lookups.
public class InventoryHighlighterOverlay extends WidgetItemOverlay
{
    private final Client client;
    private final InventoryHighlighterConfig config;
    private final ItemManager itemManager;
    private final HoverState hoverState;
    private final Cache<FillKey, Image> fillCache;

    private final Map<String, Boolean> itemPatterns = new HashMap<>();
    private final Map<String, Pattern> wildcardPatterns = new HashMap<>();
    private final Set<Integer> matchedItemIds = new HashSet<>();
    private final Map<Integer, Boolean> itemMatchCache = new HashMap<>();

    private String lastConfigList = "";

    private static final class FillKey
    {
        private final int itemId;
        private final int quantity;
        private final int color;

        private FillKey(int itemId, int quantity, Color color)
        {
            this.itemId = itemId;
            this.quantity = quantity;
            this.color = color.getRGB();
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }

            if (!(other instanceof FillKey))
            {
                return false;
            }

            FillKey fillKey = (FillKey) other;
            return itemId == fillKey.itemId
                && quantity == fillKey.quantity
                && color == fillKey.color;
        }

        @Override
        public int hashCode()
        {
            int result = itemId;
            result = 31 * result + quantity;
            result = 31 * result + color;
            return result;
        }
    }

    @Inject
    private InventoryHighlighterOverlay(Client client, InventoryHighlighterConfig config,
        ItemManager itemManager, HoverState hoverState)
    {
        this.client = client;
        this.config = config;
        this.itemManager = itemManager;
        this.hoverState = hoverState;
        this.fillCache = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .maximumSize(64)
            .build();

        showOnInventory();
        showOnBank();
        setPriority(Overlay.PRIORITY_LOW);

        updateHighlightPatterns(config.itemList());
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        if (client.getGameState() != GameState.LOGGED_IN || widgetItem == null || itemManager == null)
        {
            return;
        }

        if (!hoverState.isItemHovered(widgetItem))
        {
            return;
        }

        if (!shouldHighlightItem(itemId))
        {
            return;
        }

        drawHighlight(graphics, itemId, widgetItem);
    }

    public boolean shouldHighlightItem(int itemId)
    {
        boolean listed = isListed(itemId);
        // Blacklist inverts the raw match; an empty list means nothing is listed, so everything highlights.
        return config.highlightMode() == InventoryHighlighterConfig.HighlightMode.BLACKLIST
            ? !listed
            : listed;
    }

    // Raw, mode-agnostic match: does this item's name match one of the configured patterns?
    // The cache is keyed on this result, so toggling whitelist/blacklist never invalidates it.
    private boolean isListed(int itemId)
    {
        if (itemPatterns.isEmpty())
        {
            return false;
        }

        if (matchedItemIds.contains(itemId))
        {
            return true;
        }

        Boolean cachedMatch = itemMatchCache.get(itemId);
        if (cachedMatch != null)
        {
            return cachedMatch;
        }

        boolean matches = isItemMatch(itemId);
        itemMatchCache.put(itemId, matches);
        if (matches)
        {
            matchedItemIds.add(itemId);
        }
        return matches;
    }

    private boolean isItemMatch(int itemId)
    {
        // Plain entries are exact names; entries containing '*' use anchored wildcard matching.
        try
        {
            ItemComposition itemDef = itemManager.getItemComposition(itemId);
            if (itemDef == null || itemDef.getName() == null)
            {
                return false;
            }

            String itemName = Text.standardize(itemDef.getName()).toLowerCase();
            for (Map.Entry<String, Boolean> entry : itemPatterns.entrySet())
            {
                String pattern = entry.getKey();
                boolean isWildcard = entry.getValue();
                Pattern wildcardPattern = wildcardPatterns.get(pattern);
                if (isWildcard ? wildcardPattern.matcher(itemName).matches() : itemName.equals(pattern))
                {
                    return true;
                }
            }
        }
        catch (Exception e)
        {
            log.debug("Error matching item {}: {}", itemId, e.getMessage());
        }

        return false;
    }

    private void updateHighlightPatterns(String configList)
    {
        // Rebuild pattern caches only when the config string changes.
        String standardizedConfig = configList == null ? "" : Text.standardize(configList).toLowerCase();
        if (standardizedConfig.equals(lastConfigList))
        {
            return;
        }

        lastConfigList = standardizedConfig;
        itemPatterns.clear();
        wildcardPatterns.clear();
        matchedItemIds.clear();
        itemMatchCache.clear();

        if (standardizedConfig.isEmpty())
        {
            return;
        }

        for (String rawPattern : standardizedConfig.split(","))
        {
            String pattern = rawPattern.trim();
            if (pattern.isEmpty())
            {
                continue;
            }

            boolean isWildcard = pattern.indexOf('*') != -1;
            itemPatterns.put(pattern, isWildcard);
            if (isWildcard)
            {
                wildcardPatterns.put(pattern, Pattern.compile(toWildcardRegex(pattern)));
            }
        }
    }

    private static String toWildcardRegex(String pattern)
    {
        String[] parts = pattern.split("\\*", -1);
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < parts.length; i++)
        {
            regex.append(Pattern.quote(parts[i]));
            if (i < parts.length - 1)
            {
                regex.append(".*");
            }
        }
        regex.append('$');
        return regex.toString();
    }

    private void drawHighlight(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        // Sprite mode mirrors Inventory Tags: use ItemManager outlines and ImageUtil fills instead of custom canvas tracing.
        Rectangle bounds = widgetItem.getCanvasBounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
        {
            return;
        }

        Color originalColor = graphics.getColor();
        Stroke originalStroke = graphics.getStroke();

        try
        {
            Color outlineColor = config.outlineColor();
            Color fillColor = config.fillColor();

            if (config.spriteOnly())
            {
                if (!config.outlineOnly())
                {
                    Image filledImage = getFillImage(itemId, widgetItem.getQuantity(), fillColor);
                    graphics.drawImage(filledImage, (int) bounds.getX(), (int) bounds.getY(), null);
                }

                BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), outlineColor);
                if (outline != null)
                {
                    graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
                }
                return;
            }

            if (!config.outlineOnly())
            {
                graphics.setColor(new Color(
                    fillColor.getRed(),
                    fillColor.getGreen(),
                    fillColor.getBlue(),
                    Math.min(fillColor.getAlpha(), 130)));
                graphics.fill(bounds);
            }

            graphics.setColor(outlineColor);
            graphics.setStroke(new BasicStroke(config.outlineThickness()));
            graphics.draw(bounds);
        }
        catch (Exception e)
        {
            log.debug("Error drawing highlight: {}", e.getMessage(), e);
        }
        finally
        {
            graphics.setColor(originalColor);
            graphics.setStroke(originalStroke);
        }
    }

    private Image getFillImage(int itemId, int quantity, Color fillColor)
    {
        FillKey key = new FillKey(itemId, quantity, fillColor);
        Image image = fillCache.getIfPresent(key);
        if (image == null)
        {
            image = ImageUtil.fillImage(itemManager.getImage(itemId, quantity, false), fillColor);
            fillCache.put(key, image);
        }
        return image;
    }

    public void clearCache()
    {
        // null can never equal the (never-null) standardized config, so updateHighlightPatterns always rebuilds -
        // including when the list is cleared to empty. Using "" here would collide with an empty list and skip the rebuild.
        lastConfigList = null;
        fillCache.invalidateAll();
        updateHighlightPatterns(config.itemList());
    }
}
