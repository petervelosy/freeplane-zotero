# freeplane-zotero

A [Freeplane](https://www.freeplane.org) addon which makes it possible to assign citations from [Zotero](https://www.zotero.org/) (an excellent open source reference manager) to any node on a Freeplane mind map.

## Features
- Assign one or more citations (references) to any of your mindmap nodes
- The node will automatically have a link that opens the relevant citation(s) in Zotero
- Refresh all citations of a mind map from Zotero if you made changes to your library

![Freeplane-Zotero in action](screenshot.png)

## How to install

- Install Freeplane and Zotero if not installed yet
- Build or [download](https://github.com/petervelosy/freeplane-zotero/releases) the addon. Double click the .mm file to install it. (Alternatively, open Tools/Addons in Freeplane and browse for this file, then click Install)


## How to build
- Download and install Freeplane Developer Tools from [this page](https://www.freeplane.org/wiki/index.php/Add-ons_(install)#Developer_Tools)
- Clone this repository using Git
- Execute the following commands:

```
export FREEPLANE_DIR='...' # Your Freeplane installation directory, e.g. /usr/share/freeplane
export FREEPLANE_USER_DIR='...' # Your Freeplane user settings directory without the version number suffix. (Freeplane, Tools/Open user directory)
cd freeplane-zotero
./gradlew packageAddon
```

- The addon installation file will be located at `freeplane-zotero/build/addon/freeplane-zotero-[version].addon.mm` . Please make sure you use the .mm file including the version number, as the other one is just a copy of the addon definition file that cannot be used for installation.

## Important Notice

This addon is currently at a very early development stage and is therefore considered unstable. Feel free to report any bugs or feature requests under Issues.

All contributions are welcome!
