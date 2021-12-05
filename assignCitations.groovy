// @ExecutionModes({on_single_node="/node_popup/Zotero"})

// TODO: create two menu items (only the first ExecutionModes gets parsed)
// TODO: Implement "Jump to reference" function
// TODO: Check if Zotero is running

@Grab('com.squareup.okhttp3:okhttp:4.9.0')

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import groovy.transform.Field
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.freeplane.api.Node

// TODO: switch to Freeplane's own logger
@Field String logFileName = "freeplane_zotero.log"

@Field String zoteroConnectorUrl = "http://127.0.0.1:23119/connector"
@Field String execCommandEndpoint = "/document/execCommand"
@Field String respondEndpoint = "/document/respond"

// TODO: persist document ID
@Field UUID docId = UUID.randomUUID()
@Field String docIdStr = docId.toString()

@Field String documentData = ""

@Field Boolean zoteroProcessing = false

@Field MediaType JSON = MediaType.get("application/json; charset=utf-8");
@Field OkHttpClient client = new OkHttpClient();

@Field JsonSlurper jsonSlurper = new JsonSlurper()

void debug(String msg) {
  File log = new File(logFileName)
  log.withWriterAppend{ out ->
      out.println msg
  }
}

def postJson(url, groovyObj) {
  String json = JsonOutput.toJson(groovyObj)
  RequestBody body = RequestBody.create(JSON, json)
  Request request = new Request.Builder()
    .url(url)
  	.post(body)
  	.build();
  Response response = client.newCall(request).execute()
  return jsonSlurper.parseText(response.body().string());
}

def executeZoteroCommandInResponse(res, OkHttpClient client, Node node) {
  switch(res.command) {
    case "Application.getActiveDocument":
      return postJson(zoteroConnectorUrl + respondEndpoint, [documentID:docIdStr, outputFormat: "html", supportedNotes:[]])
      break
    case "Document.getDocumentData":
      return postJson(zoteroConnectorUrl + respondEndpoint, [dataString: documentData])
      break
    case "Document.setDocumentData":
      // TODO: Persist documentData
      documentData = res.arguments[1]
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
      return postJson(zoteroConnectorUrl + respondEndpoint, null)
      break
    case "Field.setText":
        // TODO: Parse rich text
        boolean richText = res.arguments[3]
        String newCitationText = res.arguments[2]
        def nodeTextParts = parseCitationTextFromNode(node)
        // TODO: handle citation removal
        node.text = "${nodeTextParts.title} [${newCitationText}]"
        return postJson(zoteroConnectorUrl + respondEndpoint, null)
        break
    default:
      // TODO parse showPopup command
      throw new Exception("Unable to parse Zotero request ${res.command}. Please check ~/${logFileName} for details.")
    break
  }
}

def parseCitationTextFromNode(Node node) {
  debug "Parsing node text: ${node.text}"
  def matcher = node.text =~ /([^\[\]]+)(\s+\[(.*)\])/
  if (matcher.size() > 0 && matcher[0].size() >= 4) {
    return [title: matcher[0][1], citation: matcher[0][3]]
  } else {
    return [title: node.text, citation: ""]
  }
}

//TODO: try-catch, check if Zotero is running
debug "Starting with document ID ${docIdStr}"
zoteroProcessing = true

def request = [command:'addEditCitation', docId:docIdStr]
debug request.toString()
def response = postJson(zoteroConnectorUrl + execCommandEndpoint, request)
debug response.toString()

while (zoteroProcessing) {
  response = executeZoteroCommandInResponse(response, client, node)
  if (response) {
    debug response.toString()
  }
}
debug "Citation add/edit process finished."
