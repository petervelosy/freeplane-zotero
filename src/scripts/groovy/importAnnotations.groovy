import com.petervelosy.freeplanezotero.Zotero

// TODO: run on all nodes
// @ExecutionModes({on_selected_node="//main_menu/extras/Zotero"})

def zotero = new Zotero(ui, logger, c, map)
zotero.importAnnotationsOfCitedDocuments(node)
