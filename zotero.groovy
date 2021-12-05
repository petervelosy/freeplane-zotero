// @ExecutionModes({on_single_node="/node_popup/Zotero/Insert Reference"})
// @ExecutionModes({on_single_node="/main_menu/insert/links/Zotero[Insert Reference]"})

// TODO: create two menu items (only the first ExecutionModes gets parsed)
// TODO: Implement "Jump to reference" function

@Grab('com.github.groovy-wslite:groovy-wslite:1.1.2')

import java.net.URL
import wslite.rest.*
import wslite.json.JSONObject
import wslite.json.JSONArray
import groovy.transform.Field
import org.freeplane.api.Node

@Field String logFileName = "freeplane_zotero.log"

@Field String zoteroConnectorUrl = "http://127.0.0.1:23119/connector/"
@Field String execCommandEndpoint = "/document/execCommand"
@Field String respondEndpoint = "/document/respond"

// TODO: persist document ID
@Field UUID docId = UUID.randomUUID()
@Field String docIdStr = docId.toString()

@Field String documentData = ""

@Field String fieldId
@Field String fieldCode
@Field String fieldText

@Field Boolean zoteroProcessing = false

def debug(String msg) {
  File log = new File(logFileName)
  log.withWriterAppend{ out ->
      out.println msg
  }
}

def respondZoteroRequest(JSONObject req, RESTClient client, Node node) {
  switch(req.command) {
    case "Application.getActiveDocument":
      return client.post(path:respondEndpoint) {
        json documentID:docIdStr, outputFormat: "html", supportedNotes:["footnotes"]
      }
      break
    case "Document.getDocumentData":
      return client.post(path:respondEndpoint) {
        json dataString: documentData
      }
      break
    case "Document.setDocumentData":
      // TODO: Persist documentData
      documentData = req.arguments[1]
      return client.post(path:respondEndpoint) {
        type ContentType.JSON
        text "null"
      }
      break
    case "Document.cursorInField":
      /*
      def citations = node["citations"] ? node["citations"].toString() : "" //node attributes are not Strings but Convertible objects
      def jArr = new JSONArray()
      jArr[0] = node.id
      jArr[1] = citations
      jArr[2] = 0
      return client.post(path:respondEndpoint) {
        json jArr
      }
      */
      return client.post(path:respondEndpoint) {
        type ContentType.JSON
        text "null"
      }
      break
    case "Document.canInsertField":
      return client.post(path:respondEndpoint) {
        type ContentType.JSON
        text "true"
      }
      break
    case "Document.insertField":
      fieldId = node.id
      fieldCode = "{}"
      fieldText = ""
      /*
      def jArr = new JSONArray()
      jArr[0] = fieldId
      jArr[1] = ""
      jArr[2] = 0
      */
      // {\"text\":\"{Updating}\",\"code\":\"{}\",\"id\":\"RVOP5v\",\"noteIndex\":0}
      return client.post(path:respondEndpoint) {
        json text: fieldText, code: fieldCode, id: fieldId, noteIndex: 0
      }
      break
    case "Document.getFields":
      def jArr = new JSONArray()
      jArr[0] = new JSONObject()
      jArr[0].text = fieldText
      jArr[0].code = fieldCode
      jArr[0].id = fieldId
      jArr[0].noteIndex = 0
      return client.post(path:respondEndpoint) {
        json jArr
      }
      break
    case "Field.setCode":
      fieldCode = req.arguments[2]
      node.putAt("citations", fieldCode)
      return client.post(path:respondEndpoint) {
        type ContentType.JSON
        text "null"
      }
      break
    case "Field.setText":
        // TODO: Parse rich text
        boolean richText = req.arguments[3]
        fieldText = req.arguments[2]
        // TODO: handle pattern
        node.text += " " + fieldText
        return client.post(path:respondEndpoint) {
          type ContentType.JSON
          text "null"
        }
        break
    case "Document.activate":
      return client.post(path:respondEndpoint) {
        type ContentType.JSON
        text "null"
      }
      break
    case "Document.complete":
      zoteroProcessing = false
      break
    default:
      // TODO parse showPopup command
      throw new Exception("Unable to parse Zotero request ${req.command}. Please check ~/${logFileName} for details.")
    break
  }
}

RESTClient client = new RESTClient(zoteroConnectorUrl)

//TODO: try-catch, check if Zotero is running
debug "Starting with document ID ${docIdStr}"
zoteroProcessing = true
def response = client.post(path:execCommandEndpoint, {
  json command:'addEditCitation', docId:docIdStr
})
debug response.json.toString()
while (zoteroProcessing) {
  response = respondZoteroRequest(response.json, client, node)
  if (response) {
    debug response.json.toString()
  }
}
debug "Citation add/edit process finished."
