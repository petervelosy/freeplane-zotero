// @ExecutionModes({on_single_node="//main_menu/extras/Zotero"})

import com.petervelosy.freeplanezotero.Zotero
import static com.petervelosy.freeplanezotero.Constants.*

def zotero = new Zotero(ui, logger, c, map)

zotero.executeApiCommand('refresh', node)
