// @ExecutionModes({on_single_node="/node_popup/Zotero"})

import org.freeplane.api.Node
import groovy.transform.Field

// TODO: extract to a global constants class
@Field final NODE_ATTRIBUTE_CITATIONS = "zotero_citations"

// TODO: extract to a utility class
def parseCitationTextFromNode(Node node) {
  logger.info("Parsing node text: ${node.text}")
  def matcher = node.text =~ /([^\[\]]+)(\s+\[(.*)\])/
  if (matcher.size() > 0 && matcher[0].size() >= 4) {
    return [title: matcher[0][1], citation: matcher[0][3]]
  } else {
    return [title: node.text, citation: ""]
  }
}

node[NODE_ATTRIBUTE_CITATIONS] = null
node.link = null // TODO: Only remove if it is a Zotero link
node.text = parseCitationTextFromNode(node).title
