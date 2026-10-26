package com.dot.gallery.core.metadata

import com.dot.gallery.core.metadata.MetadataCategory.CAPTURE_SETTINGS
import com.dot.gallery.core.metadata.MetadataCategory.DESCRIPTION_AUTHORSHIP
import com.dot.gallery.core.metadata.MetadataCategory.EDIT_HISTORY
import com.dot.gallery.core.metadata.MetadataCategory.IDENTITY_DEVICE
import com.dot.gallery.core.metadata.MetadataCategory.LOCATION
import com.dot.gallery.core.metadata.MetadataCategory.PEOPLE
import com.dot.gallery.core.metadata.MetadataCategory.THUMBNAIL_PREVIEW
import com.dot.gallery.core.metadata.MetadataCategory.TIMESTAMPS
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * XMP packets. Properties are classified per namespace; Google camera linkage (Motion Photo,
 * Ultra HDR gain map, depth and panorama) is always kept. The DOM is written back by a small
 * serializer that declares every namespace it uses, so removal never leaves dangling prefixes.
 */
internal object XmpMetadata {
    const val RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    private const val XMLNS = "http://www.w3.org/2000/xmlns/"
    private const val EXIF = "http://ns.adobe.com/exif/1.0/"
    private const val EXIF_EX = "http://cipa.jp/exif/1.0/"
    private const val AUX = "http://ns.adobe.com/exif/1.0/aux/"
    private const val TIFF = "http://ns.adobe.com/tiff/1.0/"
    private const val XMP = "http://ns.adobe.com/xap/1.0/"
    private const val XMP_MM = "http://ns.adobe.com/xap/1.0/mm/"
    private const val XMP_RIGHTS = "http://ns.adobe.com/xap/1.0/rights/"
    private const val CRS = "http://ns.adobe.com/camera-raw-settings/1.0/"
    private const val DC = "http://purl.org/dc/elements/1.1/"
    private const val LIGHTROOM = "http://ns.adobe.com/lightroom/1.0/"
    private const val PHOTOSHOP = "http://ns.adobe.com/photoshop/1.0/"
    private const val IPTC_CORE = "http://iptc.org/std/Iptc4xmpCore/1.0/xmlns/"
    private const val IPTC_EXT = "http://iptc.org/std/Iptc4xmpExt/2008-02-29/"
    private const val MWG_REGIONS = "http://www.metadataworkinggroup.com/schemas/regions/"
    private const val MICROSOFT_PHOTO = "http://ns.microsoft.com/photo/1.2/"
    private const val MICROSOFT_PHOTO_PREFIX = "http://ns.microsoft.com/photo/1.2/t/"

    // Points at the extended packet's digest, which changes whenever that packet is rewritten.
    private const val XMP_NOTE = "http://ns.adobe.com/xmp/note/"

    private val keptNamespaces = setOf(
        "http://ns.google.com/photos/1.0/camera/",
        "http://ns.google.com/photos/1.0/container/",
        "http://ns.google.com/photos/1.0/container/item/",
        "http://ns.google.com/photos/1.0/depthmap/",
        "http://ns.google.com/photos/1.0/image/",
        "http://ns.google.com/photos/1.0/panorama/",
        "http://ns.google.com/photos/dd/1.0/device/",
        "http://ns.adobe.com/hdr-gain-map/1.0/",
        XMP_NOTE,
        "http://ns.apple.com/HDRGainMap/1.0/",
        "http://ns.apple.com/pixeldatainfo/1.0/"
    )

    private val exifIdentity = setOf(
        "BodySerialNumber", "LensMake", "LensModel", "LensSerialNumber", "LensSpecification",
        "CameraOwnerName", "ImageUniqueID", "SerialNumber"
    )
    private val exifKeep = setOf(
        "ExifVersion", "FlashpixVersion", "ColorSpace", "PixelXDimension", "PixelYDimension",
        "ComponentsConfiguration", "NativeDigest", "Gamma", "InteroperabilityIndex"
    )

    fun classify(namespace: String?, name: String): MetadataClass = when (namespace) {
        null -> MetadataClass.Other
        in keptNamespaces -> MetadataClass.Keep
        EXIF -> when {
            name == "GPSTimeStamp" || name == "GPSDateStamp" -> MetadataClass.of(LOCATION, TIMESTAMPS)
            name.startsWith("GPS") -> MetadataClass.of(LOCATION)
            name.startsWith("DateTime") || name.startsWith("SubSecTime") || name.startsWith("OffsetTime") ->
                MetadataClass.of(TIMESTAMPS)
            name == "UserComment" -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
            name in exifIdentity -> MetadataClass.of(IDENTITY_DEVICE)
            name in exifKeep -> MetadataClass.Keep
            else -> MetadataClass.of(CAPTURE_SETTINGS)
        }
        EXIF_EX -> when (name) {
            in exifIdentity -> MetadataClass.of(IDENTITY_DEVICE)
            "Photographer", "ImageTitle" -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
            "ImageEditor", "CameraFirmware", "RAWDevelopingSoftware", "ImageEditingSoftware", "MetadataEditingSoftware" ->
                MetadataClass.of(EDIT_HISTORY)
            "Gamma", "InteroperabilityIndex" -> MetadataClass.Keep
            else -> MetadataClass.of(CAPTURE_SETTINGS)
        }
        AUX -> MetadataClass.of(IDENTITY_DEVICE)
        TIFF -> when (name) {
            "Make", "Model" -> MetadataClass.of(IDENTITY_DEVICE)
            "DateTime" -> MetadataClass.of(TIMESTAMPS)
            "ImageDescription", "Artist", "Copyright" -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
            "Software" -> MetadataClass.of(EDIT_HISTORY)
            else -> MetadataClass.Keep
        }
        XMP -> when (name) {
            "CreateDate", "ModifyDate", "MetadataDate" -> MetadataClass.of(TIMESTAMPS)
            "CreatorTool" -> MetadataClass.of(EDIT_HISTORY)
            "Rating", "Label", "Nickname", "Identifier", "BaseURL", "Advisory" -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
            "Thumbnails" -> MetadataClass.of(THUMBNAIL_PREVIEW)
            else -> MetadataClass.Other
        }
        XMP_MM, CRS -> MetadataClass.of(EDIT_HISTORY)
        DC -> if (name == "format") MetadataClass.Keep else MetadataClass.of(DESCRIPTION_AUTHORSHIP)
        XMP_RIGHTS, LIGHTROOM -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
        PHOTOSHOP -> when (name) {
            "City", "State", "Country" -> MetadataClass.of(LOCATION)
            "DateCreated" -> MetadataClass.of(TIMESTAMPS)
            "History" -> MetadataClass.of(EDIT_HISTORY)
            "ColorMode", "ICCProfile" -> MetadataClass.Keep
            else -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
        }
        IPTC_CORE -> when (name) {
            "Location", "CountryCode" -> MetadataClass.of(LOCATION)
            else -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
        }
        IPTC_EXT -> when (name) {
            "LocationCreated", "LocationShown" -> MetadataClass.of(LOCATION)
            "PersonInImage", "PersonInImageWDetails" -> MetadataClass.of(PEOPLE)
            else -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
        }
        MWG_REGIONS -> MetadataClass.of(PEOPLE)
        MICROSOFT_PHOTO -> if (name == "RegionInfo") MetadataClass.of(PEOPLE) else MetadataClass.of(DESCRIPTION_AUTHORSHIP)
        else -> if (namespace.startsWith(MICROSOFT_PHOTO_PREFIX)) MetadataClass.of(PEOPLE) else MetadataClass.Other
    }

    fun inspect(packet: ByteArray, inventory: MetadataInventory) {
        val document = parse(packet)
        properties(document).forEach { property ->
            val metadataClass = classify(property.namespace, property.name)
            inventory.record(metadataClass)
            val text = property.text
            when {
                metadataClass == MetadataClass.Keep && property.namespace in keptNamespaces && property.namespace != XMP_NOTE ->
                    inventory.functional += "xmp:${property.namespace}${property.name}" +
                        (text?.takeIf { it.length <= 64 }?.let { "=$it" } ?: "")
                metadataClass is MetadataClass.Of -> {
                    if (property.namespace == EXIF && (property.name == "GPSLatitude" || property.name == "GPSLongitude")) {
                        text?.let { inventory.locationSignature(it.toByteArray()) }
                    }
                    inventory.preview(metadataClass.categories.first(), text)
                }
            }
        }
    }

    /**
     * Returns the scrubbed packet, the input itself when nothing was removed, or null when no
     * property is left and the caller may drop the packet. With [fixedLength] the result is
     * padded to the input length (an empty packet when nothing is left) and never null.
     */
    fun scrub(packet: ByteArray, selection: MetadataSelection, fixedLength: Boolean = false): ByteArray? {
        val document = parse(packet)
        var removed = 0
        properties(document).forEach { property ->
            if (selection.removes(classify(property.namespace, property.name))) {
                property.remove()
                removed++
            }
        }
        if (removed == 0) return packet
        pruneEmptyDescriptions(document)
        val empty = properties(document).isEmpty()
        if (empty && !fixedLength) return null
        val wrapped = document.childNodes.let { nodes ->
            (0 until nodes.length).any { nodes.item(it).nodeType == Node.PROCESSING_INSTRUCTION_NODE && nodes.item(it).nodeName == "xpacket" }
        }
        val body = if (empty) EMPTY_PACKET else serialize(document, padTo = if (fixedLength) packet.size else null)
        val bytes = body.toByteArray(Charsets.UTF_8)
        if (!fixedLength) return bytes
        if (bytes.size > packet.size) malformed("Scrubbed XMP does not fit its original space")
        if (bytes.size == packet.size) return bytes
        return if (wrapped || empty) pad(bytes, packet.size) else bytes + ByteArray(packet.size - bytes.size) { 0x20 }
    }

    private const val EMPTY_PACKET = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"$RDF\"/></x:xmpmeta>"

    /** Inserts whitespace before a trailing xpacket end instruction, as XMP padding is defined. */
    private fun pad(bytes: ByteArray, size: Int): ByteArray {
        val marker = "<?xpacket end".toByteArray()
        val at = bytes.indexOf(marker).takeIf { it >= 0 } ?: bytes.size
        val padding = ByteArray(size - bytes.size) { if (it % 100 == 99) 0x0A else 0x20 }
        return bytes.copyOfRange(0, at) + padding + bytes.copyOfRange(at, bytes.size)
    }

    private class Property(val node: Node, val owner: Element) {
        val namespace: String? get() = node.namespaceURI
        val name: String get() = node.localName ?: node.nodeName.substringAfter(':')
        val text: String?
            get() = node.textContent?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }

        fun remove() {
            if (node is Attr) owner.removeAttributeNode(node) else owner.removeChild(node)
        }
    }

    private fun parse(packet: ByteArray): Document {
        var end = packet.size
        while (end > 0 && (packet[end - 1] == 0.toByte())) end--
        val text = String(packet, 0, end, Charsets.UTF_8).trimStart('﻿')
        if (text.contains("<!DOCTYPE", ignoreCase = true) || text.contains("<!ENTITY", ignoreCase = true)) {
            malformed("XMP declares a document type")
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            isValidating = false
        }
        return try {
            factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
        } catch (e: Exception) {
            throw MalformedMetadataException("XMP is not well-formed: ${e.message}")
        }
    }

    private fun descriptions(document: Document): List<Element> =
        document.getElementsByTagNameNS(RDF, "Description").let { nodes ->
            (0 until nodes.length).map { nodes.item(it) as Element }
        }.filter { (it.parentNode as? Element)?.let { parent -> parent.namespaceURI == RDF && parent.localName == "RDF" } == true }

    private fun properties(document: Document): List<Property> = descriptions(document).flatMap { description ->
        val attributes = description.attributes
        val fromAttributes = (0 until attributes.length).map { attributes.item(it) as Attr }
            .filter { it.namespaceURI != XMLNS && it.namespaceURI != RDF && it.name != "xmlns" && !it.name.startsWith("xmlns:") }
            .map { Property(it, description) }
        val children = description.childNodes
        val fromElements = (0 until children.length).map { children.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { Property(it, description) }
        fromAttributes + fromElements
    }

    private fun pruneEmptyDescriptions(document: Document) {
        descriptions(document).forEach { description ->
            val hasChildElement = (0 until description.childNodes.length).any {
                description.childNodes.item(it).nodeType == Node.ELEMENT_NODE
            }
            val hasProperty = (0 until description.attributes.length).any {
                val attribute = description.attributes.item(it) as Attr
                attribute.namespaceURI != XMLNS && attribute.namespaceURI != RDF &&
                    attribute.name != "xmlns" && !attribute.name.startsWith("xmlns:")
            }
            if (!hasChildElement && !hasProperty) description.parentNode.removeChild(description)
        }
    }

    private fun serialize(document: Document, padTo: Int?): String {
        val out = StringBuilder()
        val children = document.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            when (node.nodeType) {
                Node.ELEMENT_NODE -> writeElement(node as Element, out, emptyMap())
                Node.PROCESSING_INSTRUCTION_NODE -> out.append("<?").append(node.nodeName).append(' ').append(node.nodeValue).append("?>")
                Node.COMMENT_NODE -> out.append("<!--").append(node.nodeValue).append("-->")
            }
            if (padTo != null && node.nodeType == Node.ELEMENT_NODE) out.append('\n')
        }
        return out.toString()
    }

    private fun writeElement(element: Element, out: StringBuilder, scope: Map<String, String>) {
        val inScope = scope.toMutableMap()
        val declarations = linkedMapOf<String, String>()
        val attributes = (0 until element.attributes.length).map { element.attributes.item(it) as Attr }
        attributes.filter { it.name == "xmlns" || it.name.startsWith("xmlns:") }.forEach {
            declarations[it.name.substringAfter("xmlns", "").removePrefix(":")] = it.value
        }
        fun ensure(prefix: String?, namespace: String?) {
            if (namespace == null) return
            val key = prefix.orEmpty()
            if ((declarations[key] ?: inScope[key]) != namespace) declarations[key] = namespace
        }
        ensure(element.prefix, element.namespaceURI)
        val regular = attributes.filter { it.name != "xmlns" && !it.name.startsWith("xmlns:") }
        regular.forEach { if (it.prefix != null) ensure(it.prefix, it.namespaceURI) }
        out.append('<').append(element.tagName)
        declarations.forEach { (prefix, namespace) ->
            out.append(if (prefix.isEmpty()) " xmlns" else " xmlns:$prefix").append("=\"").append(escape(namespace, true)).append('"')
            inScope[prefix] = namespace
        }
        regular.forEach { out.append(' ').append(it.name).append("=\"").append(escape(it.value, true)).append('"') }
        val children = element.childNodes
        if (children.length == 0) {
            out.append("/>")
            return
        }
        out.append('>')
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.ELEMENT_NODE -> writeElement(child as Element, out, inScope)
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> out.append(escape(child.nodeValue, false))
                Node.PROCESSING_INSTRUCTION_NODE -> out.append("<?").append(child.nodeName).append(' ').append(child.nodeValue).append("?>")
            }
        }
        out.append("</").append(element.tagName).append('>')
    }

    private fun escape(value: String, attribute: Boolean): String = buildString(value.length) {
        value.forEach { c ->
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                attribute && c == '"' -> append("&quot;")
                attribute && c == '\n' -> append("&#xA;")
                else -> append(c)
            }
        }
    }
}
