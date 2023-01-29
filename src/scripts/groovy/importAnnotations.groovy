import com.petervelosy.freeplanezotero.Zotero

// TODO on all nodes
// @ExecutionModes({on_selected_node="//main_menu/extras/Zotero[addon.importAnnotations]"})

def zotero = new Zotero(ui, logger, c, map)
zotero.importAnnotationsOfCitedDocuments(node)
