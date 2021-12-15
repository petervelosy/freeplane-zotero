// @ExecutionModes({on_selected_node="/node_popup/Zotero"})

// TODO: create two menu items (only the first ExecutionModes gets parsed)
// TODO: implement "Refresh all citations"
// TODO: integrate the Freeplane Gradle plugin
// TODO: Ability to transform links from online to offline
// TODO: Only add a link automatically if no link exists or the link is a Zotero link
// TODO: Set 'Show selected attributes only' as a default and hide zotero_ node attributes
// TODO: Handle access control errors -> notify the user to set script permissions in Freeplane
// TODO: Another integration scenario: create a semantic network (with labellable/classifiable connectors/edges) between many papers (extract references using e.g. https://github.com/CeON/CERMINE)
// TODO: hierarchical node numbering?
// TODO: copy citations from parent

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

if (!zotero.getDocumentProperty(zotero.STORAGE_KEY_DOCUMENT_ID, node)) {
  UUID uu = UUID.randomUUID()
  node.mindMap.storage[zotero.STORAGE_KEY_DOCUMENT_ID] = uu.toString()
  node.mindMap.storage[zotero.STORAGE_KEY_DOCUMENT_DATA] = ""
  menuUtils.executeMenuItems(['ShowSelectedAttributesAction'])
}
logger.info("Starting with document ID ${zotero.getDocumentProperty(zotero.STORAGE_KEY_DOCUMENT_ID, node)}")
logger.info("Document properties: ${zotero.propertiesToObj(node.mindMap.storage).toString()}")

zotero.executeApiCommand('addEditCitation', node)
