# Протокол Minecraft PE Alpha 0.8.x (клиентская реализация)

Документ описывает подмножество протокола, реализованное в NostalgiaPE. Все
значения — часть открыто задокументированного сетевого формата классических
серверов PE (PocketMine / NostalgiaCore). Реализация написана заново
(clean-room) и не содержит чужого кода.

## Транспорт: RakNet «structure 5»

- Все многобайтовые целые в RakNet-заголовках — **big-endian**, кроме
  24-битных «triad» (номера датаграмм / индексы сообщений) — они
  **little-endian**.
- MAGIC (16 байт): `00 FF FF 00 FE FE FE FE FD FD FD FD 12 34 56 78`.

### Offline-хендшейк

| Пакет | ID | Основные поля |
|---|---|---|
| OpenConnectionRequest1 | `0x05` | MAGIC, structure=5, паддинг под MTU |
| OpenConnectionReply1 | `0x06` | — |
| OpenConnectionRequest2 | `0x07` | MAGIC, адрес сервера, MTU (BE short), clientId (BE long) |
| OpenConnectionReply2 | `0x08` | — |

### Датаграммы данных

`DATA_PACKET_*` = `0x80..0x8F`. Заголовок: 1 байт id + 3 байта (LE triad)
номера последовательности, далее — одно или несколько инкапсулированных
сообщений:

```
flags (1 байт)            // reliability = (flags & 0xE0) >> 5, split = flags & 0x10
lengthBits (BE short)     // длина полезной нагрузки в БИТАХ
[messageIndex]  (LE triad, если reliable)
[sequenceIndex] (LE triad, если sequenced)
[orderIndex]    (LE triad) + orderChannel (1 байт), если ordered
[split: count(BE int), id(BE short), index(BE int)]
payload (length байт)     // первый байт = игровой packet id
```

Клиент отправляет игровые пакеты как **reliable-ordered** (reliability = 3) на
канале 0 и подтверждает входящие датаграммы пакетами **ACK** (`0xC0`).

## Игровой слой (MCPE 0.8.x)

Строки — `BE ushort length + UTF-8 bytes`. Числа `float` — IEEE-754 big-endian.

| Пакет | ID | Направление | Формат тела |
|---|---|---|---|
| Login | `0x82` | → сервер | string name, int proto1, int proto2, int clientId, string payload |
| LoginStatus | `0x83` | ← сервер | int status (0 = success) |
| Ready | `0x84` | → сервер | byte status |
| Message | `0x85` | ← сервер | string source, string message |
| SetTime | `0x86` | ← сервер | int time, bool stop |
| StartGame | `0x87` | ← сервер | int seed, int gen, int gamemode, int eid, int spawnX/Y/Z, float x/y/z |
| MovePlayer | `0x95` | ↔ | int eid, float x/y/z, float yaw, float pitch, float bodyYaw |
| RemoveBlock | `0x97` | → сервер | int eid, int x, int z, byte y |
| UpdateBlock | `0x98` | ← сервер | int x, int z, byte y, byte id, byte meta |
| UseItem | `0xA3` | → сервер | int x/y/z, int face, ushort item, byte meta, int eid, float fx/fy/fz, float px/py/pz |
| Chat | `0xB6` | → сервер | string message |
| FullChunkData | `0xBA` | ← сервер | zlib(LE int x, LE int z, blockIds[32768], meta[16384], skyLight, blockLight, heightmap[256], biome[256], tileNBT) |

`PROTOCOL_VERSION` клиента = **14** (значение из Alpha 0.8.1).

### Формат чанка

Блоки в `FullChunkData` идут в порядке **X-Z-Y**:

```
index = (x * 16 + z) * 128 + y      // x,z ∈ [0..15], y ∈ [0..127]
```

Это ровно тот же layout, что использует внутренний массив чанка клиента, поэтому
блоки копируются напрямую. `World` хранит только id блоков (метаданные, свет и
NBT сущностей в текущей версии не отрисовываются).

## Что НЕ реализовано

- Ретрансмиссия по NACK (клиент полагается на надёжность своих исходящих и ACK
  входящих).
- Инвентарь, сущности, звуки.

Приём разбитых пакетов (split) **реализован**: фрагменты собираются по `splitId`,
поэтому крупные чанки (`FullChunkData`) принимаются корректно.

Остальные пункты — в дорожной карте (`README.md`).
