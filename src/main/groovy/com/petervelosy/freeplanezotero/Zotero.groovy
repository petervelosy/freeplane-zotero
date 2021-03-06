package com.petervelosy.freeplanezotero

import static Constants.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.freeplane.api.Node
import java.util.concurrent.TimeUnit
import java.net.ConnectException
import java.net.URI
import org.apache.commons.text.StringEscapeUtils
import javax.swing.JOptionPane

class Zotero {

    private ui
    private logger
    private controller
    private map
    private JsonSlurper jsonSlurper
    private OkHttpClient client
    private zoteroProcessing = false

    Zotero(ui, logger, controller, map) {
        this.ui = ui
        this.logger = logger
        this.controller = controller
        this.map = map
        this.jsonSlurper = new JsonSlurper()
        // We need a reasonably long read timeout, since we need to keep the connection open
        // until the user chooses a citation:
        this.client = new OkHttpClient.Builder()
                .readTimeout(1, TimeUnit.MINUTES)
                .build();
    }


    def executeApiCommand(command, node) {
        zoteroProcessing = true

        def request = [command:command, docId:getDocumentProperty(STORAGE_KEY_DOCUMENT_ID, node)]
        try {
            def response = postJson(ZOTERO_CONNECTOR_URL + EXEC_COMMAND_ENDPOINT, request)
            while (zoteroProcessing) {
                response = executeZoteroCommandInResponse(response, client, node)
            }
            logger.info("API transaction ${command} finished.")
        } catch (ConnectException e) {
            throw new ZoteroIntegrationException("Unable to connect to the Zotero HTTP API. Please ensure Zotero is running.", e)
        } catch (ApiException e) {
            if (e.message == "Integration transaction is already in progress") {
                throw new ZoteroIntegrationException("The last Zotero interaction did not complete successfully, therefore, Zotero's integration API is in an unknown state. Please restart Zotero and try again.", e)
            } else {
                throw e
            }
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
            throw new ApiException(respStr)
        }
    }

    def executeZoteroCommandInResponse(res, OkHttpClient client, Node node) {
        switch(res.command) {
            case "Application.getActiveDocument":
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, [documentID:getDocumentProperty(STORAGE_KEY_DOCUMENT_ID, node), outputFormat: "html", supportedNotes:[]])
            case "Document.getDocumentData":
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, [dataString: getDocumentProperty(STORAGE_KEY_DOCUMENT_DATA, node)])
            case "Document.setDocumentData":
                node.mindMap.storage[STORAGE_KEY_DOCUMENT_DATA] = res.arguments[1]
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, null)
            case "Document.cursorInField":
                if (node[NODE_ATTRIBUTE_CITATIONS]) {
                    //node[NODE_ATTRIBUTE_CITATIONS] is a Convertible, not a String:
                    return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, [text: parseCitationTextFromNode(node).citation, code: node[NODE_ATTRIBUTE_CITATIONS].toString(), id: node.id, noteIndex: 0])
                } else {
                    return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, null)
                }
            case "Document.canInsertField":
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, true)
            case "Document.insertField":
                node[NODE_ATTRIBUTE_CITATIONS] = "{}"
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, [text: "", code: node[NODE_ATTRIBUTE_CITATIONS].toString(), id: node.id, noteIndex: 0])
            case "Document.getFields":
                def fields = this.controller.findAll().findResults { n ->
                    if (n[NODE_ATTRIBUTE_CITATIONS]) {
                        return [text: parseCitationTextFromNode(n).citation, code: n[NODE_ATTRIBUTE_CITATIONS].toString(), id: n.id, noteIndex: 0]
                    } else {
                        return null
                    }
                }
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, fields)
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
                                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, 1)
                            case JOptionPane.CANCEL_OPTION:
                                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, 0)
                        }
                        break
                    case ZOTERO_DIALOG_BUTTONS_YES_NO:
                        def result = ui.showConfirmDialog(null, message, "", JOptionPane.YES_NO_OPTION, javaIconId)
                        switch (result) {
                            case JOptionPane.YES_OPTION:
                                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, 1)
                            case JOptionPane.NO_OPTION:
                                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, 0)
                        }
                        break
                    case ZOTERO_DIALOG_BUTTONS_YES_NO_CANCEL:
                        def result = ui.showConfirmDialog(null, message, "", JOptionPane.YES_NO_CANCEL_OPTION, javaIconId)
                        switch (result) {
                            case JOptionPane.YES_OPTION:
                                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, 2)
                            case JOptionPane.NO_OPTION:
                                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, 1)
                            case JOptionPane.CANCEL_OPTION:
                                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, 0)
                        }
                        break
                    case ZOTERO_DIALOG_BUTTONS_OK:
                        ui.informationMessage(null, message, "", javaIconId)
                        return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, 1)
                }
                break
            case "Document.activate":
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, null)
            case "Document.complete":
                zoteroProcessing = false
                break
            case "Field.select":
                def fieldId = res.arguments[1]
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, null)
            case "Field.delete":
                def fieldId = res.arguments[1]
                def referredNode = this.map.node(fieldId)
                referredNode.putAt(NODE_ATTRIBUTE_CITATIONS, null)
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, null)
            case "Field.removeCode":
                def fieldId = res.arguments[1]
                def referredNode = this.map.node(fieldId)
                referredNode.putAt(NODE_ATTRIBUTE_CITATIONS, "")
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, null)
            case "Field.setCode":
                def fieldId = res.arguments[1]
                def fieldCode = res.arguments[2]
                def referredNode = this.map.node(fieldId)
                referredNode.putAt(NODE_ATTRIBUTE_CITATIONS, fieldCode)
                if (fieldCode.startsWith(FIELD_CODE_PREFIX_CSL)) {
                    def csl = parseCslFieldCode(fieldCode)
                    def itemIds = extractItemIdsFromCsl(csl)
                    def link = generateLocalZoteroLinkFromItemIds(itemIds)
                    referredNode.link.setUri(new URI(link))
                }
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, null)
            case "Field.getText":
                def fieldId = res.arguments[1]
                def referredNode = this.map.node(fieldId)
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, parseCitationTextFromNode(referredNode).citation)
            case "Field.setText":
                def fieldId = res.arguments[1]
                String newCitationText = res.arguments[2]
                // TODO: Parse rich text
                boolean richText = res.arguments[3]
                if (richText) {
                    newCitationText = StringEscapeUtils.unescapeHtml4(newCitationText)
                }
                def referredNode = this.map.node(fieldId)
                def nodeTextParts = parseCitationTextFromNode(referredNode)
                referredNode.text = "${nodeTextParts.title} [${newCitationText}]"
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, null)
            default:
                throw new Exception("Unable to parse Zotero request ${res.command}. Please check Freeplane's log file for details.")
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

}
