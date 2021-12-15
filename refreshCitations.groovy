// @ExecutionModes({on_single_node="//main_menu/extras/Zotero"})

import groovy.transform.SourceURI
import java.nio.file.Path
import java.nio.file.Paths

@SourceURI
URI sourceUri

Path scriptLocation = Paths.get(sourceUri)

GroovyShell shell = new GroovyShell()
def zotero = shell.parse(scriptLocation.resolveSibling('zotero.groovy').toFile())
zotero.logger = logger
zotero.ui = ui

zotero.executeApiCommand('refresh', node)
