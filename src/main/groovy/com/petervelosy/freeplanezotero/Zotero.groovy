package com.petervelosy.freeplanezotero

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.Driver

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.freeplane.api.Node
import java.util.concurrent.TimeUnit

import org.apache.commons.text.StringEscapeUtils
import javax.swing.JOptionPane

import static com.petervelosy.freeplanezotero.Constants.*

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
                .build()
    }


    def executeApiCommand(command, node) {
        zoteroProcessing = true

        def request = [command: command, docId: getDocumentProperty(STORAGE_KEY_DOCUMENT_ID, node)]
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

    def postJson(String url, groovyObj) {
        logger.info("Request to URL ${url}: ${groovyObj.toString()}")
        String json = JsonOutput.toJson(groovyObj)
        RequestBody body = RequestBody.create(JSON, json)
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build()
        Response response = client.newCall(request).execute()
        String respStr = response.body().string()
        logger.info("Response: ${respStr}")
        if (response.successful) {
            return jsonSlurper.parseText(respStr)
        } else {
            throw new ApiException(respStr)
        }
    }

    def executeZoteroCommandInResponse(res, OkHttpClient client, Node node) {
        switch (res.command) {
            case "Application.getActiveDocument":
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, [documentID: getDocumentProperty(STORAGE_KEY_DOCUMENT_ID, node), outputFormat: "html", supportedNotes: []])
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
                def fields = this.controller.findAll().findResults { Node n ->
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
                switch (buttons) {
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
                referredNode[NODE_ATTRIBUTE_CITATIONS] = null
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, null)
            case "Field.removeCode":
                def fieldId = res.arguments[1]
                def referredNode = this.map.node(fieldId)
                referredNode[NODE_ATTRIBUTE_CITATIONS] = ""
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, null)
            case "Field.setCode":
                def fieldId = res.arguments[1]
                String fieldCode = res.arguments[2]
                def referredNode = this.map.node(fieldId)
                referredNode[NODE_ATTRIBUTE_CITATIONS] = fieldCode
                if (fieldCode.startsWith(FIELD_CODE_PREFIX_CSL)) {
                    def csl = parseCslFieldCode(fieldCode)
                    def itemIds = extractItemKeysFromCsl(csl)
                    def link = generateLocalZoteroLinkFromItemIds(itemIds)
                    referredNode.link.setUri(new URI(link))
                }
                return postJson(ZOTERO_CONNECTOR_URL + RESPOND_ENDPOINT, null)
            case "Field.getText":
                def fieldId = res.arguments[1]
                Node referredNode = this.map.node(fieldId)
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
        switch (iconId) {
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
        csl.citationItems.collect { it.id }
    }

    def extractItemKeysFromCsl(csl) {
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

    // TODO: run on all nodes of the current map
    def importAnnotationsOfCitedDocuments(Node node) {
        // TODO add link to annotation
        if (!nodeHasCitations(node)) {
            throw new ZoteroIntegrationException("Please assign at least one citation to this node in order to be able to import the annotations in the cited document(s).")
        }
        def csl = parseCslFieldCode(node[NODE_ATTRIBUTE_CITATIONS].toString())
        def citedWorkItemIds = extractItemIdsFromCsl(csl)
        def citedWorksItemIdsStr = citedWorkItemIds.join(", ")

        // FIXME: DriverManager does not seem to be able to load the JDBC driver from Freeplane's library path for addons. Looks like a classloader issue.
        def cls = Class.forName("org.sqlite.JDBC")
        Driver driver = (Driver) (cls.getDeclaredConstructor().newInstance())
        def userHome = System.getProperty("user.home")
        def dbFileName = "${userHome}/Zotero/zotero.sqlite"
        def dbFileTempName = dbFileName.replace(".sqlite", "_temp.sqlite")

        // We need to copy Zotero's database every time, as Zotero holds an exclusive lock on it while it is running.
        // I haven't found any SQLite JDBC options which would nevertheless allow for a read-only access
        // TODO: need to check whether copying an open DB works on Windows...
        def zoteroDb = new File(dbFileName)
        def zoteroDbCopy = new File(dbFileTempName)
        try {
            Files.copy(zoteroDb.toPath(), zoteroDbCopy.toPath(), StandardCopyOption.REPLACE_EXISTING)

            try (Connection conn = driver.connect("jdbc:sqlite:${dbFileTempName}", new Properties())) {
                def sql = "select ann.itemID as itemID, att.parentItemID as parentItemID, ann.text as text, ann.comment as comment, ann.pageLabel as pageLabel from itemAnnotations ann left join itemAttachments att on att.itemID = ann.parentItemID where att.parentItemID in(${citedWorksItemIdsStr})"
                def stmt = conn.prepareStatement(sql)
                def resultSet = stmt.executeQuery()

                if (!resultSet.isBeforeFirst() && resultSet.getRow() == 0) {
                    JOptionPane.showMessageDialog(null, "This document has no annotations.")
                }
                while (resultSet.next()) {
                    def itemID = resultSet.getString("itemID")
                    def parentItemID = resultSet.getString("parentItemID")
                    def highlightedText = resultSet.getString("text")
                    def comment = resultSet.getString("comment")
                    def pageLabel = resultSet.getString("pageLabel")

                    if (!highlightedText && !isAnnotationIgnored(itemID, "comment")) {
                        importSingleCommentAnnotation(itemID, node, comment)
                    } else {
                        importHighlightedTextWithCommentAnnotation(itemID, node, highlightedText, parentItemID, pageLabel, comment)
                    }
                }
            }
        } finally {
            zoteroDbCopy.delete()
            // This refreshes all citations in the document despite the node parameter:
            executeApiCommand('refresh', node)
        }
    }

    private void importSingleCommentAnnotation(itemID, Node node, comment) {
        def nodesFound = controller.find { it[NODE_ATTRIBUTE_ANNOTATION_ITEM_ID]?.toString() == itemID.toString() && it[NODE_ATTRIBUTE_ANNOTATION_FIELD] == "comment" }
        if (nodesFound.empty) {
            def childNode = node.createChild()
            setAsCommentNode(childNode, itemID, comment)
        } else {
            nodesFound.each { setAsCommentNode(it, itemID, comment) }
        }
    }

    private void importHighlightedTextWithCommentAnnotation(itemID, Node node, highlightedText, parentItemID, pageLabel, comment) {
        def textNodes = []
        def textIgnored = isAnnotationIgnored(itemID, "text")
        if (!textIgnored) {
            def textNodesFound = controller.find { it[NODE_ATTRIBUTE_ANNOTATION_ITEM_ID]?.toString() == itemID.toString() && it[NODE_ATTRIBUTE_ANNOTATION_FIELD] == "text" }
            if (textNodesFound.empty) {
                def childNode = node.createChild()
                setAsAnnotationTextNode(childNode, itemID, highlightedText, node, parentItemID, pageLabel)
                textNodes.add(childNode)
            } else {
                textNodesFound.each { setAsAnnotationTextNode(it, itemID, highlightedText, node, parentItemID, pageLabel) }
                textNodes.addAll(textNodesFound)
            }
        }

        def commentIgnored = isAnnotationIgnored(itemID, "comment")
        if (comment && !commentIgnored) {
            def commentNodesFound = controller.find { it[NODE_ATTRIBUTE_ANNOTATION_ITEM_ID]?.toString() == itemID.toString() && it[NODE_ATTRIBUTE_ANNOTATION_FIELD] == "comment" }
            if (commentNodesFound.empty) {
                if (textIgnored) {
                    // Attach node directly to the node with the citation:
                    def childNode = node.createChild()
                    setAsCommentNode(childNode, itemID, comment)
                } else {
                    textNodes.each { Node textNode ->
                        def childNode = textNode.createChild()
                        setAsCommentNode(childNode, itemID, comment)
                    }
                }
            } else {
                commentNodesFound.each { setAsCommentNode(it, itemID, comment) }
            }
        }
    }

    static void addAnnotationToIgnoreList(map, annItemIdAttribute, annFieldAttribute) {
        if (!map.storage[STORAGE_KEY_ANNOTATION_IGNORE_LIST]) {
            map.storage[STORAGE_KEY_ANNOTATION_IGNORE_LIST] = "[" + annItemIdAttribute.value.toString() + ":" + annFieldAttribute.value.toString() + "]"
        } else {
            map.storage[STORAGE_KEY_ANNOTATION_IGNORE_LIST] += (",[" + annItemIdAttribute.value.toString() + ":" + annFieldAttribute.value.toString() + "]")
        }
    }

    private boolean isAnnotationIgnored(itemId, field) {
        def ignoreListKey = "[${itemId}:${field}]"
        def ignored = map.storage[STORAGE_KEY_ANNOTATION_IGNORE_LIST] && map.storage[STORAGE_KEY_ANNOTATION_IGNORE_LIST].contains(ignoreListKey)
        return ignored
    }

    private setAsAnnotationTextNode(node, itemID, text, parentNode, citedDocumentItemId, pageLabel) {
        def parsedCitationNode = parseCitationTextFromNode(parentNode)
        node[NODE_ATTRIBUTE_ANNOTATION_ITEM_ID] = itemID
        node[NODE_ATTRIBUTE_ANNOTATION_FIELD] = "text"
        node[NODE_ATTRIBUTE_CITATIONS] = generateAnnotationCsl(parentNode, citedDocumentItemId, pageLabel)
        node.setText("\"${text}\" [${parsedCitationNode.citation}]")
    }

    private generateAnnotationCsl(parentNode, citedDocumentItemId, pageLabel) {
        def csl = parseCslFieldCode(parentNode[NODE_ATTRIBUTE_CITATIONS].toString())
        csl.citationItems = csl.citationItems.findAll{it -> it.itemData.id.toString() == citedDocumentItemId}
        csl.citationItems[0].locator = pageLabel
        csl.citationItems[0].label = "page"
        return "${FIELD_CODE_PREFIX_CSL}${JsonOutput.toJson(csl)}"
    }

    private setAsCommentNode(node, itemID, comment) {
        node[NODE_ATTRIBUTE_ANNOTATION_ITEM_ID] = itemID
        node[NODE_ATTRIBUTE_ANNOTATION_FIELD] = "comment"
        node.setText(comment)
    }

    private nodeHasCitations(node) {
        return !!node[NODE_ATTRIBUTE_CITATIONS]
    }

}
