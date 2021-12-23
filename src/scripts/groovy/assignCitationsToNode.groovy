// @ExecutionModes({on_selected_node="/node_popup/Zotero"})

// TODO: create two menu items - popup+main menu (only the first 'ExecutionModes' gets parsed)
// TODO: Ability to transform links from online to offline
// TODO: Only add a link automatically if no link exists or the link is a Zotero link
// TODO: Handle access control errors -> notify the user to set script permissions in Freeplane
// TODO: Another integration scenario: create a semantic network (with labellable/classifiable connectors/edges) between many papers (extract references using e.g. https://github.com/CeON/CERMINE)
// TODO: hierarchical node numbering?
// TODO: copy citations from parent
// TODO: sign scripts
// TODO: test multi-select scenarios

import com.petervelosy.freeplanezotero.Zotero
import com.petervelosy.freeplanezotero.Constants

def zotero = new Zotero(ui, logger, c, map)

if (!zotero.getDocumentProperty(Constants.STORAGE_KEY_DOCUMENT_ID, node)) {
    UUID uu = UUID.randomUUID()
    node.mindMap.storage[Constants.STORAGE_KEY_DOCUMENT_ID] = uu.toString()
    node.mindMap.storage[Constants.STORAGE_KEY_DOCUMENT_DATA] = ""
    // Set node attribute display mode to "Show selected attributes only". This is to hide the long
    // citation attributes that would otherwise clutter the display
    menuUtils.executeMenuItems([
        'ShowSelectedAttributesAction'
    ])
}
logger.info("Starting with document ID ${zotero.getDocumentProperty(Constants.STORAGE_KEY_DOCUMENT_ID, node)}")
logger.info("Document properties: ${zotero.propertiesToObj(node.mindMap.storage).toString()}")

zotero.executeApiCommand('addEditCitation', node)
