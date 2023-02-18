import javax.swing.JOptionPane

// @ExecutionModes({on_selected_node="//main_menu/extras/Zotero"})

// TODO validate format
def updatedIgnoreList = JOptionPane.showInputDialog(null, "Ignore list of annotations not to import from Zotero (format: [itemId:field],[itemId:field], where field is either of text or comment):", map.storage["zotero_annotation_ignore_list"])
if (updatedIgnoreList) {
    map.storage["zotero_annotation_ignore_list"] = updatedIgnoreList
}