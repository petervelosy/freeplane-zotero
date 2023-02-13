package com.petervelosy.freeplanezotero

import okhttp3.MediaType

class Constants {

    public static final ZOTERO_CONNECTOR_URL = "http://127.0.0.1:23119/connector"
    public static final EXEC_COMMAND_ENDPOINT = "/document/execCommand"
    public static final RESPOND_ENDPOINT = "/document/respond"

    public static final STORAGE_KEY_DOCUMENT_ID = "zotero_document_id"
    public static final STORAGE_KEY_DOCUMENT_DATA = "zotero_document_data"

    public static final NODE_ATTRIBUTE_CITATIONS = "zotero_citations"
    public static final NODE_ATTRIBUTE_ANNOTATION_ITEM_ID = "zotero_annotation_item_id"
    public static final NODE_ATTRIBUTE_ANNOTATION_FIELD = "zotero_annotation_field"

    public static final FIELD_CODE_PREFIX_CSL = "ITEM CSL_CITATION "

    public static final ZOTERO_DIALOG_ICON_STOP = 0
    public static final ZOTERO_DIALOG_ICON_NOTICE = 1
    public static final ZOTERO_DIALOG_ICON_CAUTION = 2

    public static final ZOTERO_DIALOG_BUTTONS_OK = 0
    public static final ZOTERO_DIALOG_BUTTONS_OK_CANCEL = 1
    public static final ZOTERO_DIALOG_BUTTONS_YES_NO = 2
    public static final ZOTERO_DIALOG_BUTTONS_YES_NO_CANCEL = 3

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
}