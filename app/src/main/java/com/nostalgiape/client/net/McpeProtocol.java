package com.nostalgiape.client.net;

/**
 * MCPE 0.8.1 game-level packet identifiers (protocol 14 client / server
 * protocol 18 in NostalgiaCore terms). These ids are part of the public
 * wire protocol; the values below are documented constants, not code.
 */
public final class McpeProtocol {
    private McpeProtocol() {}

    public static final int PROTOCOL_VERSION = 14;

    public static final int LOGIN = 0x82;
    public static final int LOGIN_STATUS = 0x83;
    public static final int READY = 0x84;
    public static final int MESSAGE = 0x85;
    public static final int SET_TIME = 0x86;
    public static final int START_GAME = 0x87;
    public static final int ADD_MOB = 0x88;
    public static final int ADD_PLAYER = 0x89;
    public static final int REMOVE_PLAYER = 0x8a;
    public static final int ADD_ENTITY = 0x8c;
    public static final int REMOVE_ENTITY = 0x8d;
    public static final int ADD_ITEM_ENTITY = 0x8e;
    public static final int TAKE_ITEM_ENTITY = 0x8f;
    public static final int MOVE_ENTITY = 0x90;
    public static final int MOVE_ENTITY_POSROT = 0x93;
    public static final int ROTATE_HEAD = 0x94;
    public static final int MOVE_PLAYER = 0x95;
    public static final int REMOVE_BLOCK = 0x97;
    public static final int UPDATE_BLOCK = 0x98;
    public static final int ADD_PAINTING = 0x99;
    public static final int EXPLODE = 0x9a;
    public static final int LEVEL_EVENT = 0x9b;
    public static final int TILE_EVENT = 0x9c;
    public static final int ENTITY_EVENT = 0x9d;
    public static final int REQUEST_CHUNK = 0x9e;
    public static final int CHUNK_DATA = 0x9f;
    public static final int PLAYER_EQUIPMENT = 0xa0;
    public static final int PLAYER_ARMOR_EQUIPMENT = 0xa1;
    public static final int INTERACT = 0xa2;
    public static final int USE_ITEM = 0xa3;
    public static final int PLAYER_ACTION = 0xa4;
    public static final int HURT_ARMOR = 0xa6;
    public static final int SET_ENTITY_DATA = 0xa7;
    public static final int SET_ENTITY_MOTION = 0xa8;
    public static final int SET_ENTITY_LINK = 0xa9;
    public static final int SET_HEALTH = 0xaa;
    public static final int SET_SPAWN_POSITION = 0xab;
    public static final int ANIMATE = 0xac;
    public static final int RESPAWN = 0xad;
    public static final int SEND_INVENTORY = 0xae;
    public static final int DROP_ITEM = 0xaf;
    public static final int CONTAINER_OPEN = 0xb0;
    public static final int CONTAINER_CLOSE = 0xb1;
    public static final int CONTAINER_SET_SLOT = 0xb2;
    public static final int CONTAINER_SET_DATA = 0xb3;
    public static final int CONTAINER_SET_CONTENT = 0xb4;
    public static final int CHAT = 0xb6;
    public static final int ADVENTURE_SETTINGS = 0xb7;
    public static final int ENTITY_DATA = 0xb8;
    public static final int PLAYER_INPUT = 0xb9;
    public static final int FULL_CHUNK_DATA = 0xba;
    public static final int UNLOAD_CHUNK = 0xbb;

    // Login status codes
    public static final int STATUS_LOGIN_SUCCESS = 0;
    public static final int STATUS_LOGIN_FAILED_CLIENT = 1;
    public static final int STATUS_LOGIN_FAILED_SERVER = 2;
}
