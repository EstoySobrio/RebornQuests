# Compilar RebornQuests (Forge 1.12.2)

RebornQuests es un mod de Forge para **Minecraft 1.12.2**. Se compila con JDK 8 y Gradle 4.10.2.

> **Descargo de responsabilidad:** lee [DESCARGO.md](DESCARGO.md). El mod se distribuye "tal cual",
> sin garantía, y no está afiliado a Custom NPCs, LuckPerms, Forge ni Mojang. La compatibilidad con
> servidores híbridos también se detalla ahí.

## Requisitos

| Cosa | Valor |
|------|-------|
| JDK | 8 |
| Gradle | 4.10.2 |
| Forge | 1.12.2-14.23.5.2847, mapeos MCP `stable_39` |
| Red | no hace falta: se compila `--offline` |

## Compilar

Desde la carpeta del mod:

```bash
JAVA_HOME=<ruta-jdk8> <ruta-gradle>/bin/gradle build --offline
```

La salida es `build/libs/RebornQuests-<version>.jar`. El número de versión está en `build.gradle`
(línea `version`); súbelo de 0.0.1 en cada entrega para distinguir los jars.

El jar es universal: vale para el cliente y para el servidor. Cópialo a la carpeta `mods` de tu
cliente o servidor y deja solo una versión activa (renombra la anterior como `.jar.off` en vez de
borrarla). Si el servidor está abierto, el jar está bloqueado: ciérralo antes de cambiarlo.

## Dependencias

| Mod / plugin | Papel | ¿Obligatorio? |
|--------------|-------|---------------|
| **Custom NPCs** (1.12.2) | de él se leen las misiones; los paneles y las aldeas son del mod y se crean desde el panel admin | Sí |
| **LuckPerms** (5.x) | de ahí se leen los rangos (grupos) y el nodo de administración `staff` | Sí para el sistema de permisos |

**LuckPerms es un requisito del sistema de permisos.** Como LuckPerms es un plugin, para usarlo el
mod necesita un **servidor compatible con plugins y mods a la vez** (un servidor híbrido que ejecute
plugins de Bukkit junto con mods de Forge). El mod **solo lee** de LuckPerms: nunca escribe ni
modifica sus datos.

Sin plugin de permisos —singleplayer, LAN o un Forge puro— no hay nodo que comprobar y el acceso se
resuelve con el operador (op) del servidor, del nivel `luckperms.opWithoutBridge` (4 por defecto)
junto con el dueño del mundo local. Se mira la lista de op del servidor y no `canUseCommand`, para
que un invitado de una partida abierta no se cuele como mando. Con plugin de permisos, el mando es
siempre su nodo `staff`, nunca el op.

> El repo **no incluye jars de terceros**. Debes obtener Custom NPCs y LuckPerms desde sus fuentes
> oficiales y cumplir sus licencias. Para compilar, coloca el jar de Custom NPCs en
> `libs/customnpcs.jar` (solo aporta las firmas de la API de misiones; el servidor siempre usa su
> propia copia).

## Estructura

```
dev.sobrio.rebornquests
  RebornQuests          registro del mod, red de paquetes, re-registro del comando en caliente
  RQConfig              todas las opciones; settings() es el esquema que edita la UI
  BoardService          monta el tablón (paneles + progreso + gate + tiempos + expiración)
  QuestBridge           lectura del libro de misiones de Custom NPCs
  QuestGuards           las mismas restricciones en las otras rutas de aceptación de CNPC
  MissionStore          reloj de aceptaciones y terminaciones, un archivo por mundo
  RankService           puente de lectura con LuckPerms; op y dueño del mundo si no hay plugin
  MissionEvents         clic al NPC del tablón, aviso de rango al entrar, caducidad cada 20 s
  AdminAction           el panel de administración envuelve sus cambios aquí
  BoardData             el viaje del tablón al cliente (paneles, filas, ajustes, catálogo)
  CnpcWatch             repasa los archivos de Custom NPCs y firma quién los tocó
  NpcAudit              compara los NPCs guardados; lotes de descarga e importación
  AuditStore            el registro, solo de añadir, comprimido y oculto
  RateGate              cuánto tarda el servidor en contestar la misma pregunta
  CommandRebornMisiones /rebornmisiones y alias; sin argumentos abre el panel admin
  client/
    GuiMisiones         el tablón que ve el jugador, y su cuadro de Información de rango
    GuiAdminPanel       la pantalla de administración con siete pestañas
    QuestLogHook        sustituye la ventana de misiones de CNPC y avisa de las sesiones de editor
    NpcEditorUi         la pantalla del editor de CNPC
    HudTimeLeft         el reloj de misiones en pantalla
    Dynasty             colores y marcos de las dos pantallas
```