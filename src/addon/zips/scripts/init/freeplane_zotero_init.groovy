import org.freeplane.features.map.IMapChangeListener
import org.freeplane.features.map.NodeDeletionEvent
import org.freeplane.features.map.NodeModel
import org.freeplane.features.mode.Controller
import org.freeplane.features.attribute.IAttributeTableModel
import org.freeplane.features.attribute.NodeAttributeTableModel
import static com.petervelosy.freeplanezotero.Constants.*
import org.freeplane.plugin.script.proxy.ScriptUtils
import com.petervelosy.freeplanezotero.Zotero

import javax.swing.JOptionPane

class NodeDeletionListener implements IMapChangeListener {

    def ui

    public NodeDeletionListener(ui) {
        this.ui = ui
    }

    public void onNodeDeleted(NodeDeletionEvent event) {
        NodeModel nodeModel = event.node
        final IAttributeTableModel attributes = NodeAttributeTableModel.getModel(nodeModel);

        def oAnnItemIdAttribute = attributes.getAttributes().stream().filter(attr -> attr.getName().equals(NODE_ATTRIBUTE_ANNOTATION_ITEM_ID)).findAny()
        def oAnnFieldAttribute = attributes.getAttributes().stream().filter(attr -> attr.getName().equals(NODE_ATTRIBUTE_ANNOTATION_FIELD)).findAny()

        if (oAnnItemIdAttribute.isPresent() && oAnnFieldAttribute.isPresent()) {

            def annItemIdAttribute = oAnnItemIdAttribute.get()
            def annFieldAttribute = oAnnFieldAttribute.get()

            def map = ScriptUtils.node().map

            def result = ui.showConfirmDialog(null, "The node you have deleted was created from a Zotero annotation. Would you like to ignore this annotation so that it won't re-appear on future annotation imports from Zotero?", "", JOptionPane.YES_NO_OPTION)
            switch (result) {
                case JOptionPane.YES_OPTION:
                    Zotero.addAnnotationToIgnoreList(map, annItemIdAttribute, annFieldAttribute)
                    break
                case JOptionPane.NO_OPTION:
                    break
            }
        }
    }
}

def mapController = Controller.currentModeController.mapController
mapController.nodeChangeListeners.findAll {
    it.getClass().name == NodeDeletionListener.class.name
}.each {
    println "removeMapChangeListener($it)"
    mapController.removeMapChangeListener(it)
}
mapController.addMapChangeListener(new NodeDeletionListener(ui))