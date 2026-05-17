package pro.sketchware.ai.tools.impl.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import pro.sketchware.ai.tools.Tool;

/**
 * AnalyzeXmlTool — Parses and analyzes XML content.
 *
 * <p>Returns the root element, attribute count, child element count,
 * a pretty-printed version, and an optional attribute listing.
 *
 * <p><b>Expected JSON input:</b>
 * <pre>
 * {
 *   "xml_text":     "&lt;root&gt;...&lt;/root&gt;",
 *   "element_query": "LinearLayout"   // optional: find elements by tag name
 * }
 * </pre>
 */
public class AnalyzeXmlTool implements Tool {

    public static final String NAME = "analyze_xml";

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Parses and analyzes XML content. Returns root element info, "
                + "attribute listing, child element count, and pretty-printed output. "
                + "Supports optional element tag query.";
    }

    @Nullable
    @Override
    public String getInputSchema() {
        return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "  \"xml_text\":{\"type\":\"string\",\"description\":\"The raw XML string to analyze\"},"
                + "  \"element_query\":{\"type\":\"string\",\"description\":\"Optional tag name to find (e.g., 'LinearLayout')\"}"
                + "},"
                + "\"required\":[\"xml_text\"]"
                + "}";
    }

    @NonNull
    @Override
    public ToolResult execute(@Nullable String jsonInput) {
        // ── 1. Parse tool input ────────────────────────────────────────────
        if (jsonInput == null || jsonInput.trim().isEmpty()) {
            return ToolResult.failure(NAME,
                    "Input required. Provide {\"xml_text\": \"...\"}");
        }

        String xmlText;
        String elementQuery;
        try {
            JSONObject input = new JSONObject(jsonInput);
            xmlText      = input.optString("xml_text", "").trim();
            elementQuery = input.optString("element_query", "").trim();
        } catch (JSONException e) {
            return ToolResult.failure(NAME, "Invalid tool input JSON: " + e.getMessage());
        }

        if (xmlText.isEmpty()) {
            return ToolResult.failure(NAME, "'xml_text' field is required.");
        }

        // ── 2. Parse XML ───────────────────────────────────────────────────
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Security: disable external entity processing
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new ByteArrayInputStream(
                    xmlText.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

        } catch (SAXException e) {
            return ToolResult.failure(NAME,
                    "Invalid XML: " + e.getMessage());
        } catch (IOException e) {
            return ToolResult.failure(NAME,
                    "Failed to read XML data: " + e.getMessage());
        } catch (ParserConfigurationException e) {
            return ToolResult.failure(NAME,
                    "XML parser configuration error: " + e.getMessage());
        }

        // ── 3. Analyze root element ────────────────────────────────────────
        Element root = doc.getDocumentElement();
        StringBuilder sb = new StringBuilder();

        sb.append("Root Element: <").append(root.getNodeName()).append(">\n");
        sb.append("Attributes (").append(root.getAttributes().getLength()).append("):\n");

        NamedNodeMap attrs = root.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            sb.append("  ").append(attr.getNodeName())
              .append(" = \"").append(attr.getNodeValue()).append("\"\n");
        }

        // Direct child elements
        NodeList children = root.getChildNodes();
        int elementChildCount = 0;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                elementChildCount++;
            }
        }
        sb.append("Direct Child Elements: ").append(elementChildCount).append('\n');

        // ── 4. Optional element query ──────────────────────────────────────
        if (!elementQuery.isEmpty()) {
            NodeList found = doc.getElementsByTagName(elementQuery);
            sb.append("\n─── Elements matching '<").append(elementQuery)
              .append(">': ").append(found.getLength()).append(" found ───\n");

            for (int i = 0; i < Math.min(found.getLength(), 5); i++) {
                Element el = (Element) found.item(i);
                sb.append("  [").append(i + 1).append("] ");
                sb.append('<').append(el.getNodeName());
                NamedNodeMap elAttrs = el.getAttributes();
                for (int j = 0; j < elAttrs.getLength(); j++) {
                    Node a = elAttrs.item(j);
                    sb.append(' ').append(a.getNodeName())
                      .append("=\"").append(a.getNodeValue()).append('"');
                }
                sb.append(">\n");
            }
            if (found.getLength() > 5) {
                sb.append("  ... and ").append(found.getLength() - 5).append(" more.\n");
            }
        }

        // ── 5. Pretty print ────────────────────────────────────────────────
        sb.append("\n─── Pretty-printed XML ───\n");
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            sb.append(writer.toString());
        } catch (TransformerException e) {
            sb.append("[Pretty-print failed: ").append(e.getMessage()).append("]");
        }

        return ToolResult.success(sb.toString(), "application/xml");
    }
}
