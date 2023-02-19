import org.freeplane.features.map.IMapChangeListener
import org.freeplane.features.map.NodeDeletionEvent
import org.freeplane.features.map.NodeModel
import org.freeplane.features.mode.Controller
import org.freeplane.features.attribute.IAttributeTableModel
import org.freeplane.features.attribute.NodeAttributeTableModel
import static com.petervelosy.freeplanezotero.Constants.*
import org.freeplane.plugin.script.proxy.ScriptUtils

import javax.swing.JOptionPane

class NodeDeletionListener implements IMapChangeListener {

    def ui

    public NodeDeletionListener(ui) {
        this.ui = ui
    }

    public void onNodeDeleted(NodeDeletionEvent event) {
        NodeModel nodeModel = event.node
        final IAttributeTableModel attributes = NodeAttributeTableModel.getModel(nodeModel);

        // TODO: replace map keys with constants:
        def oAnnItemIdAttribute = attributes.getAttributes().stream().filter(attr -> attr.getName().equals("zotero_annotation_item_id")).findAny()
        def oAnnFieldAttribute = attributes.getAttributes().stream().filter(attr -> attr.getName().equals("zotero_annotation_field")).findAny()

        if (oAnnItemIdAttribute.isPresent() && oAnnFieldAttribute.isPresent()) {

            def annItemIdAttribute = oAnnItemIdAttribute.get()
            def annFieldAttribute = oAnnFieldAttribute.get()

            def map = ScriptUtils.node().map

            //JOptionPane.showMessageDialog(null, map.storage["zotero_annotation_ignore_list"])
            def result = ui.showConfirmDialog(null, "The node you have deleted was created from a Zotero annotation. Would you like to ignore this annotation so that it won't re-appear on future annotation imports from Zotero?", "", JOptionPane.YES_NO_OPTION)
            switch (result) {
                case JOptionPane.YES_OPTION:
                    addAnnotationToIgnoreList(map, annItemIdAttribute, annFieldAttribute)
                    //JOptionPane.showMessageDialog(null, map.storage["zotero_annotation_ignore_list"])
                    break
                case JOptionPane.NO_OPTION:
                    break
            }
        }
    }

    // TODO extract into Zotero.groovy or another class
    private void addAnnotationToIgnoreList(map, annItemIdAttribute, annFieldAttribute) {
        if (!map.storage["zotero_annotation_ignore_list"]) {
            map.storage["zotero_annotation_ignore_list"] = "[" + annItemIdAttribute.value.toString() + ":" + annFieldAttribute.value.toString() + "]"
        } else {
            map.storage["zotero_annotation_ignore_list"] += (",[" + annItemIdAttribute.value.toString() + ":" + annFieldAttribute.value.toString() + "]")
        }
    }
}

//
// add IMapChangeListener
//
def mapController = Controller.currentModeController.mapController
mapController.nodeChangeListeners.findAll {
    it.getClass().name == NodeDeletionListener.class.name
}.each {
    println "removeMapChangeListener($it)"
    mapController.removeMapChangeListener(it)
}
mapController.addMapChangeListener(new NodeDeletionListener(ui))