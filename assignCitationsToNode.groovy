// @ExecutionModes({on_single_node="/node_popup/Zotero"})

// TODO: create two menu items (only the first ExecutionModes gets parsed)
// TODO: Implement "Jump to reference" function
// TODO: implement "Refresh all citations"
// TODO: Handle this: INFO: Response: {"command":"Document.displayAlert","arguments":["371f5adf-8a4d-4429-9478-2f0ee62947a5","You have modified this citation since Zotero generated it. Editing will clear your modifications. Do you want to continue?\n\nOriginal: (Bóna et al., 1986; Szabó-Jilek & Dr Rózsa, 1977)\nModified: (Bóna et al., 1986; Szabó-Jilek &#38; Dr Rózsa, 1977)\n",1,1]}
// TODO: Field.delete
// TODO: integrate the Freeplane Gradle plugin
// TODO: Ability to transform links from online to offline
// TODO: Only add a link automatically if no link exists or the link is a Zotero link

@Grab('com.squareup.okhttp3:okhttp:4.9.0')
@Grab('org.apache.commons:commons-lang3:3.12.0')

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import groovy.transform.Field
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.freeplane.api.Node
import java.util.concurrent.TimeUnit
import java.net.ConnectException
import java.net.URI
import org.apache.commons.lang.StringEscapeUtils

@Field String zoteroConnectorUrl = "http://127.0.0.1:23119/connector"
@Field String execCommandEndpoint = "/document/execCommand"
@Field String respondEndpoint = "/document/respond"

@Field final STORAGE_KEY_DOCUMENT_ID = "zotero_document_id"
@Field final STORAGE_KEY_DOCUMENT_DATA = "zotero_document_data"

@Field final FIELD_CODE_PREFIX_CSL = "ITEM CSL_CITATION "

@Field Boolean zoteroProcessing = false

@Field MediaType JSON = MediaType.get("application/json; charset=utf-8");
// We need a reasonably long read timeout, since we need to keep the connection open
// until the user chooses a citation:
@Field OkHttpClient client = new OkHttpClient.Builder()
      .readTimeout(1, TimeUnit.MINUTES)
      .build();

@Field JsonSlurper jsonSlurper = new JsonSlurper()

def postJson(url, groovyObj) {
  logger.info("Request to URL ${url}: ${groovyObj.toString()}")
  String json = JsonOutput.toJson(groovyObj)
  RequestBody body = RequestBody.create(JSON, json)
  Request request = new Request.Builder()
    .url(url)
  	.post(body)
  	.build();
  Response response = client.newCall(request).execute()
  String respStr = response.body().string()
  logger.info("Response: ${respStr}")
  if (response.successful) {
    return jsonSlurper.parseText(respStr);
  } else {
    throw new Exception(respStr)
  }
}

def executeZoteroCommandInResponse(res, OkHttpClient client, Node node) {
  switch(res.command) {
    case "Application.getActiveDocument":
      return postJson(zoteroConnectorUrl + respondEndpoint, [documentID:getDocumentProperty(STORAGE_KEY_DOCUMENT_ID, node), outputFormat: "html", supportedNotes:[]])
      break
    case "Document.getDocumentData":
      return postJson(zoteroConnectorUrl + respondEndpoint, [dataString: getDocumentProperty(STORAGE_KEY_DOCUMENT_DATA, node)])
      break
    case "Document.setDocumentData":
      node.mindMap.storage[STORAGE_KEY_DOCUMENT_DATA] = res.arguments[1]
      return postJson(zoteroConnectorUrl + respondEndpoint, null)
      break
    case "Document.cursorInField":
      if (node["citations"]) {
        //node["citations"] is a Convertible, not a String:
        return postJson(zoteroConnectorUrl + respondEndpoint, [text: parseCitationTextFromNode(node).citation, code: node["citations"].toString(), id: node.id, noteIndex: 0])
      } else {
        return postJson(zoteroConnectorUrl + respondEndpoint, null)
      }
      break
    case "Document.canInsertField":
      return postJson(zoteroConnectorUrl + respondEndpoint, true)
      break
    case "Document.insertField":
      node["citations"] = "{}"
      node.minimized = true
      return postJson(zoteroConnectorUrl + respondEndpoint, [text: "", code: node["citations"].toString(), id: node.id, noteIndex: 0])
      break
    case "Document.getFields":
      return postJson(zoteroConnectorUrl + respondEndpoint, [[text: parseCitationTextFromNode(node).citation, code: node["citations"].toString(), id: node.id, noteIndex: 0]])
      break
    case "Document.activate":
      return postJson(zoteroConnectorUrl + respondEndpoint, null)
      break
    case "Document.complete":
      zoteroProcessing = false
      break
    case "Field.setCode":
      def fieldCode = res.arguments[2]
      node.putAt("citations", fieldCode)
      if (fieldCode.startsWith(FIELD_CODE_PREFIX_CSL)) {
        def csl = parseCslFieldCode(fieldCode)
        def itemIds = extractItemIdsFromCsl(csl)
        def link = generateLocalZoteroLinkFromItemIds(itemIds)
        node.link.setUri(new URI(link))
      }
      return postJson(zoteroConnectorUrl + respondEndpoint, null)
      break
    case "Field.setText":
        // TODO: Parse rich text
        boolean richText = res.arguments[3]
        String newCitationText = res.arguments[2]
        if (richText) {
          newCitationText = StringEscapeUtils.unescapeHtml(newCitationText)
        }
        def nodeTextParts = parseCitationTextFromNode(node)
        // TODO: handle citation removal
        node.text = "${nodeTextParts.title} [${newCitationText}]"
        return postJson(zoteroConnectorUrl + respondEndpoint, null)
        break
    default:
      // TODO parse the Document.displayAlert command
      throw new Exception("Unable to parse Zotero request ${res.command}. Please check Freeplane's log file for details.")
    break
  }
}

def parseCitationTextFromNode(Node node) {
  logger.info("Parsing node text: ${node.text}")
  def matcher = node.text =~ /([^\[\]]+)(\s+\[(.*)\])/
  if (matcher.size() > 0 && matcher[0].size() >= 4) {
    return [title: matcher[0][1], citation: matcher[0][3]]
  } else {
    return [title: node.text, citation: ""]
  }
}

def parseCslFieldCode(String fieldCode) {
  String jsonPartStr = fieldCode.substring(FIELD_CODE_PREFIX_CSL.length())
  return jsonSlurper.parseText(jsonPartStr)
}

def extractItemIdsFromCsl(csl) {
  // TODO: check if an URI is present even if the Zotero library is not synced with a cloud account
  csl.citationItems.collect { it.uris[0].split("/").last() }
}

def generateLocalZoteroLinkFromItemIds(itemIds) {
  "zotero://select/library/items?itemKey=${itemIds.join(',')}"
}

def propertiesToObj(properties) {
  def result = [:]
  properties.keySet().each {
    result[it] = properties[it]
  }
  return result
}

def getDocumentProperty(key, node) {
  // Storage values are otherwise retrieved as Convertibles. Practical as they are, they are unfortunately recursive.
  return node.mindMap.storage[key]?.toString()
}

if (!getDocumentProperty(STORAGE_KEY_DOCUMENT_ID, node)) {
  UUID uu = UUID.randomUUID()
  node.mindMap.storage[STORAGE_KEY_DOCUMENT_ID] = uu.toString()
  node.mindMap.storage[STORAGE_KEY_DOCUMENT_DATA] = ""
}
logger.info("Starting with document ID ${getDocumentProperty(STORAGE_KEY_DOCUMENT_ID, node)}")
logger.info("Document properties: ${propertiesToObj(node.mindMap.storage).toString()}")
zoteroProcessing = true

def request = [command:'addEditCitation', docId:getDocumentProperty(STORAGE_KEY_DOCUMENT_ID, node)]
try {
  def response = postJson(zoteroConnectorUrl + execCommandEndpoint, request)
  while (zoteroProcessing) {
    response = executeZoteroCommandInResponse(response, client, node)
  }
  logger.info("Citation add/edit process finished.")
} catch (ConnectException e) {
  throw new Exception("Unable to connect to the Zotero HTTP API. Please ensure Zotero is running.", e)
}
