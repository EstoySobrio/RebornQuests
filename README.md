# RebornQuests

Tablón de misiones para **Minecraft 1.12.2 (Forge)** que lee el libro de misiones de
**Custom NPCs** y los rangos de **LuckPerms**, y los presenta en una interfaz propia con
paneles por rango y aldea, progreso, tiempos límite y un panel de administración.

> Mod no oficial. Se distribuye "tal cual", sin garantía. Lee el
> [descargo de responsabilidad](DESCARGO.md) antes de usarlo.

## Características

- Tablón que sustituye la ventana de misiones de Custom NPCs, con paneles por rango y aldea.
- Dificultad (D…S), cooldown y tiempo límite **por misión**, no por panel.
- Panel de administración: el mando lo da el nodo `staff`, no el op.
- Rangos leídos de los grupos de LuckPerms; aldea por nodo de permiso.
- Auditoría de cambios de Custom NPCs: solo de añadir, comprimida y oculta.
- HUD de tiempo restante y configuración editable dentro del juego.

## Requisitos

| Dependencia | Papel |
|-------------|-------|
| Minecraft Forge 1.12.2 | plataforma del mod |
| Custom NPCs 1.12.2 | fuente de las misiones |
| LuckPerms 5.x | rangos y permisos |

**LuckPerms es un requisito del sistema de permisos.** Como es un plugin, el mod necesita un
**servidor compatible con plugins y mods a la vez** (un servidor híbrido). En singleplayer o
un Forge puro sin plugin de permisos, el acceso se resuelve con el op del servidor y el dueño
del mundo local. El mod **solo lee** de LuckPerms y Custom NPCs; nunca los modifica.

La compatibilidad con servidores y dónde se ha probado se detalla en [DESCARGO.md](DESCARGO.md).

## Compilar

Requiere **JDK 8** y **Gradle 4.10.2**. Instrucciones completas en
[COMO-COMPILAR.md](COMO-COMPILAR.md). El repo no incluye jars de terceros: coloca el jar de
Custom NPCs en `libs/customnpcs.jar` para compilar (solo aporta las firmas de la API).

```bash
JAVA_HOME=<ruta-jdk8> <ruta-gradle>/bin/gradle build --offline
```

## Instalación

Copia `RebornQuests-<version>.jar` (salida en `build/libs/`) a la carpeta `mods` de tu cliente
o servidor. Deja solo una versión activa; renombra la anterior como `.jar.off` en vez de borrarla.

## Errores y compatibilidad

¿Un fallo en tu servidor? Abre un [issue](https://github.com/EstoySobrio/RebornQuests/issues)
indicando el servidor y su versión, la versión del mod y el log del error.

## Licencia

[MIT](LICENSE) © 2026 Sobrio. Este proyecto no está afiliado a Custom NPCs, LuckPerms, Forge
ni Mojang. Todas las marcas pertenecen a sus respectivos propietarios.