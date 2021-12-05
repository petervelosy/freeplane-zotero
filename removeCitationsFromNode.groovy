// @ExecutionModes({on_single_node="/node_popup/Zotero"})

import org.freeplane.api.Node

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

node["citations"] = null
node.text = parseCitationTextFromNode(node).title
