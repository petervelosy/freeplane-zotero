// @ExecutionModes({on_selected_node="/node_popup/Zotero"})

import com.petervelosy.freeplanezotero.Zotero
import com.petervelosy.freeplanezotero.Constants

def zotero = new Zotero(ui, logger)

node[Constants.NODE_ATTRIBUTE_CITATIONS] = null
node.link.remove() // TODO: Only remove if it is a Zotero link
node.text = zotero.parseCitationTextFromNode(node).title
