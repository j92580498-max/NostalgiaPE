package com.nostalgiape.client.world;

/**
 * Classic Minecraft PE block ids and their base colours. Colours are used by
 * the software/GL renderer as a stand-in for textures; they are original
 * approximations, not extracted texture assets.
 */
public final class Block {
    private Block() {}

    public static final int AIR = 0;
    public static final int STONE = 1;
    public static final int GRASS = 2;
    public static final int DIRT = 3;
    public static final int COBBLESTONE = 4;
    public static final int PLANKS = 5;
    public static final int SAPLING = 6;
    public static final int BEDROCK = 7;
    public static final int WATER = 8;
    public static final int STILL_WATER = 9;
    public static final int LAVA = 10;
    public static final int STILL_LAVA = 11;
    public static final int SAND = 12;
    public static final int GRAVEL = 13;
    public static final int GOLD_ORE = 14;
    public static final int IRON_ORE = 15;
    public static final int COAL_ORE = 16;
    public static final int WOOD = 17;
    public static final int LEAVES = 18;
    public static final int GLASS = 20;
    public static final int WOOL = 35;
    public static final int FLOWER_YELLOW = 37;
    public static final int FLOWER_RED = 38;
    public static final int BRICK = 45;
    public static final int TNT = 46;
    public static final int BOOKSHELF = 47;
    public static final int MOSSY_COBBLE = 48;
    public static final int OBSIDIAN = 49;
    public static final int TORCH = 50;

    /** Returns true if the block is see-through / non-solid for face culling. */
    public static boolean isTransparent(int id) {
        switch (id) {
            case AIR:
            case WATER:
            case STILL_WATER:
            case GLASS:
            case LEAVES:
            case SAPLING:
            case FLOWER_RED:
            case FLOWER_YELLOW:
            case TORCH:
                return true;
            default:
                return false;
        }
    }

    public static boolean isSolid(int id) {
        switch (id) {
            case AIR:
            case WATER:
            case STILL_WATER:
            case LAVA:
            case STILL_LAVA:
            case SAPLING:
            case FLOWER_RED:
            case FLOWER_YELLOW:
            case TORCH:
                return false;
            default:
                return true;
        }
    }

    /** RGB colour (0xRRGGBB) used to shade a block face. Original palette. */
    public static int color(int id) {
        switch (id) {
            case STONE: return 0x7F7F7F;
            case GRASS: return 0x5C8A3A;
            case DIRT: return 0x8B6D4B;
            case COBBLESTONE: return 0x6B6B6B;
            case PLANKS: return 0xB1905A;
            case BEDROCK: return 0x333333;
            case WATER:
            case STILL_WATER: return 0x3A6BCB;
            case LAVA:
            case STILL_LAVA: return 0xD8631A;
            case SAND: return 0xDDD3A0;
            case GRAVEL: return 0x8A8583;
            case GOLD_ORE: return 0x9C8B60;
            case IRON_ORE: return 0x9B8A7C;
            case COAL_ORE: return 0x4A4A4A;
            case WOOD: return 0x6E5433;
            case LEAVES: return 0x3E7A28;
            case GLASS: return 0xC6E8F0;
            case WOOL: return 0xE8E8E8;
            case BRICK: return 0x9B4A32;
            case TNT: return 0xC63A2A;
            case BOOKSHELF: return 0xA9884E;
            case MOSSY_COBBLE: return 0x5E7355;
            case OBSIDIAN: return 0x1A1626;
            case TORCH: return 0xFFD060;
            case FLOWER_YELLOW: return 0xE8D628;
            case FLOWER_RED: return 0xD62828;
            default: return 0x9060C0;
        }
    }
}
