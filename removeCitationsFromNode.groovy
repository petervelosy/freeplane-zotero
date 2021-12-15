// @ExecutionModes({on_selected_node="/node_popup/Zotero"})

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

node[zotero.NODE_ATTRIBUTE_CITATIONS] = null
node.link.remove() // TODO: Only remove if it is a Zotero link
node.text = zotero.parseCitationTextFromNode(node).title
