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
import javax.swing.JOptionPane

@Field String zoteroConnectorUrl = "http://127.0.0.1:23119/connector"
@Field String execCommandEndpoint = "/document/execCommand"
@Field String respondEndpoint = "/document/respond"

@Field final STORAGE_KEY_DOCUMENT_ID = "zotero_document_id"
@Field final STORAGE_KEY_DOCUMENT_DATA = "zotero_document_data"

@Field final NODE_ATTRIBUTE_CITATIONS = "zotero_citations"

@Field final FIELD_CODE_PREFIX_CSL = "ITEM CSL_CITATION "

@Field final ZOTERO_DIALOG_ICON_STOP = 0
@Field final ZOTERO_DIALOG_ICON_NOTICE = 1
@Field final ZOTERO_DIALOG_ICON_CAUTION = 2

@Field final ZOTERO_DIALOG_BUTTONS_OK = 0
@Field final ZOTERO_DIALOG_BUTTONS_OK_CANCEL = 1
@Field final ZOTERO_DIALOG_BUTTONS_YES_NO = 2
@Field final ZOTERO_DIALOG_BUTTONS_YES_NO_CANCEL = 3

@Field Boolean zoteroProcessing = false

@Field MediaType JSON = MediaType.get("application/json; charset=utf-8");
// We need a reasonably long read timeout, since we need to keep the connection open
// until the user chooses a citation:
@Field OkHttpClient client = new OkHttpClient.Builder()
      .readTimeout(1, TimeUnit.MINUTES)
      .build();

@Field JsonSlurper jsonSlurper = new JsonSlurper()

@Field ui
@Field logger

def executeApiCommand(command, node) {
  zoteroProcessing = true

  def request = [command:command, docId:getDocumentProperty(STORAGE_KEY_DOCUMENT_ID, node)]
  try {
    def response = postJson(zoteroConnectorUrl + execCommandEndpoint, request)
    while (zoteroProcessing) {
      response = executeZoteroCommandInResponse(response, client, node)
    }
    logger.info("Citation add/edit process finished.")
  } catch (ConnectException e) {
    throw new Exception("Unable to connect to the Zotero HTTP API. Please ensure Zotero is running.", e)
  }
}

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
    case "Document.getDocumentData":
      return postJson(zoteroConnectorUrl + respondEndpoint, [dataString: getDocumentProperty(STORAGE_KEY_DOCUMENT_DATA, node)])
    case "Document.setDocumentData":
      node.mindMap.storage[STORAGE_KEY_DOCUMENT_DATA] = res.arguments[1]
      return postJson(zoteroConnectorUrl + respondEndpoint, null)
    case "Document.cursorInField":
      if (node[NODE_ATTRIBUTE_CITATIONS]) {
        //node[NODE_ATTRIBUTE_CITATIONS] is a Convertible, not a String:
        return postJson(zoteroConnectorUrl + respondEndpoint, [text: parseCitationTextFromNode(node).citation, code: node[NODE_ATTRIBUTE_CITATIONS].toString(), id: node.id, noteIndex: 0])
      } else {
        return postJson(zoteroConnectorUrl + respondEndpoint, null)
      }
    case "Document.canInsertField":
      return postJson(zoteroConnectorUrl + respondEndpoint, true)
    case "Document.insertField":
      node[NODE_ATTRIBUTE_CITATIONS] = "{}"
      return postJson(zoteroConnectorUrl + respondEndpoint, [text: "", code: node[NODE_ATTRIBUTE_CITATIONS].toString(), id: node.id, noteIndex: 0])
    case "Document.getFields":
      return postJson(zoteroConnectorUrl + respondEndpoint, [[text: parseCitationTextFromNode(node).citation, code: node[NODE_ATTRIBUTE_CITATIONS].toString(), id: node.id, noteIndex: 0]])
    case "Document.displayAlert":
      def message = res.arguments[1]
      def icon = res.arguments[2]
      def buttons = res.arguments[3]
      def javaIconId = zoteroToJavaIconId(icon)
      switch(buttons) {
        case ZOTERO_DIALOG_BUTTONS_OK_CANCEL:
          def result = ui.showConfirmDialog(null, message, "", JOptionPane.OK_CANCEL_OPTION, javaIconId)
          switch (result) {
            case JOptionPane.OK_OPTION:
              return postJson(zoteroConnectorUrl + respondEndpoint, 1)
            case JOptionPane.CANCEL_OPTION:
              return postJson(zoteroConnectorUrl + respondEndpoint, 0)
          }
          break
        case ZOTERO_DIALOG_BUTTONS_YES_NO:
          def result = ui.showConfirmDialog(null, message, "", JOptionPane.YES_NO_OPTION, javaIconId)
          switch (result) {
            case JOptionPane.YES_OPTION:
              return postJson(zoteroConnectorUrl + respondEndpoint, 1)
            case JOptionPane.NO_OPTION:
              return postJson(zoteroConnectorUrl + respondEndpoint, 0)
          }
          break
        case ZOTERO_DIALOG_BUTTONS_YES_NO_CANCEL:
          def result = ui.showConfirmDialog(null, message, "", JOptionPane.YES_NO_CANCEL_OPTION, javaIconId)
          switch (result) {
            case JOptionPane.YES_OPTION:
              return postJson(zoteroConnectorUrl + respondEndpoint, 2)
            case JOptionPane.NO_OPTION:
              return postJson(zoteroConnectorUrl + respondEndpoint, 1)
            case JOptionPane.CANCEL_OPTION:
              return postJson(zoteroConnectorUrl + respondEndpoint, 0)
          }
          break
        case ZOTERO_DIALOG_BUTTONS_OK:
          ui.informationMessage(message, "", javaIconId)
          return postJson(zoteroConnectorUrl + respondEndpoint, 1)
      }
      break
    case "Document.activate":
      return postJson(zoteroConnectorUrl + respondEndpoint, null)
    case "Document.complete":
      zoteroProcessing = false
      break
    // FIXME: Field methods currently only work on the current node
    case "Field.select":
      def fieldId = res.arguments[1]
      return postJson(zoteroConnectorUrl + respondEndpoint, null)
    case "Field.delete":
      def fieldId = res.arguments[1]
      node.putAt(NODE_ATTRIBUTE_CITATIONS, null)
      return postJson(zoteroConnectorUrl + respondEndpoint, null)
    case "Field.removeCode":
      def fieldId = res.arguments[1]
      node.putAt(NODE_ATTRIBUTE_CITATIONS, "")
      return postJson(zoteroConnectorUrl + respondEndpoint, null)
    case "Field.setCode":
      def fieldCode = res.arguments[2]
      node.putAt(NODE_ATTRIBUTE_CITATIONS, fieldCode)
      if (fieldCode.startsWith(FIELD_CODE_PREFIX_CSL)) {
        def csl = parseCslFieldCode(fieldCode)
        def itemIds = extractItemIdsFromCsl(csl)
        def link = generateLocalZoteroLinkFromItemIds(itemIds)
        node.link.setUri(new URI(link))
      }
      return postJson(zoteroConnectorUrl + respondEndpoint, null)
    case "Field.getText":
      def fieldId = res.arguments[1]
      return postJson(zoteroConnectorUrl + respondEndpoint, parseCitationTextFromNode(node).citation)
    case "Field.setText":
        // TODO: Parse rich text
        boolean richText = res.arguments[3]
        String newCitationText = res.arguments[2]
        if (richText) {
          newCitationText = StringEscapeUtils.unescapeHtml(newCitationText)
        }
        def nodeTextParts = parseCitationTextFromNode(node)
        node.text = "${nodeTextParts.title} [${newCitationText}]"
        return postJson(zoteroConnectorUrl + respondEndpoint, null)
    default:
      throw new Exception("Unable to parse Zotero request ${res.command}. Please check Freeplane's log file for details.")
    break
  }
}

def zoteroToJavaIconId(iconId) {
  switch(iconId) {
    case ZOTERO_DIALOG_ICON_STOP:
      return JOptionPane.ERROR_MESSAGE
    case ZOTERO_DIALOG_ICON_CAUTION:
      return JOptionPane.WARNING_MESSAGE
    case ZOTERO_DIALOG_ICON_NOTICE:
      return JOptionPane.INFORMATION_MESSAGE
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
