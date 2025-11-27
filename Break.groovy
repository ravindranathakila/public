// @ExecutionModes({ON_SINGLE_NODE})

@Grab('com.fasterxml.jackson.core:jackson-databind:2.18.0')
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

import groovy.json.JsonSlurper
import org.freeplane.core.ui.components.UITools

/****************************************************
 * CONFIG SECTION
 ****************************************************/

// Ollama chat API endpoint
//String ollamaUrl = 'http://localhost:11434/api/chat'
String openai = 'https://api.openai.com/v1/responses'

// Model to use for decomposition
//String modelName = 'phi4'
//String modelName = 'llama3.2:3b'
//String modelName = 'mistral'
String modelName = 'gemma2:9b'

// Maximum recommended depth of bullets (the LLM is instructed, not enforced)
int suggestedMaxDepth = 3

/****************************************************
 * HELPER: Create child nodes recursively from bullet JSON
 ****************************************************/

void createChildrenFromBullets(def parentNode, List bullets) {
    if (!bullets) return

    bullets.each { b ->
        if (!b) return
        def text = (b.text ?: "").toString().trim()
        if (!text) return

        def child = parentNode.createChild(text)
        child.folded = false

        def children = b.children
        if (children instanceof List && !children.isEmpty()) {
            createChildrenFromBullets(child, children)
        }
    }
}

/****************************************************
 * MAIN
 ****************************************************/

def current = node
String sourceText = (current.text ?: "").trim()

if (!sourceText) {
    UITools.informationMessage("Current node has no text to decompose.")
    return
}

try {
    def client = HttpClient.newHttpClient()
    def mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    def jsonSchema = [
            "\$schema"          : "http://json-schema.org/draft-07/schema#",
            title               : "HierarchicalBulletOutline",
            type                : "object",
            additionalProperties: false,
            properties          : [
                    bullets: [
                            type : "array",
                            items: [
                                    "\$ref": "#/definitions/bullet"
                            ]
                    ]
            ],
            required            : ["bullets"],
            definitions         : [
                    bullet: [
                            type                : "object",
                            additionalProperties: false,
                            properties          : [
                                    text    : [
                                            type: "string"
                                    ],
                                    children: [
                                            type : "array",
                                            items: [
                                                    "\$ref": "#/definitions/bullet"
                                            ]
                                    ]
                            ],
                            required            : ["text", "children"]
                    ]
            ]
    ]


    // Prompt: ask the LLM to produce a hierarchical bullet structure as JSON
    def prompt = """
Your job is to break down the text, and only the text within double-quotes into a Hierarchical Bullet Outline, similar to how a careful human would turn a complex paragraph into nested bullet points, following these rues:
1. Read the text and identify its main ideas and supporting details.
2. Construct a tree of bullets:
   - Top-level bullets represent the main ideas.
   - Each bullet may have a 'children' array with sub-bullets representing more specific details or sub-steps.
   - Avoid trivial fragmentation: do not split sentences into meaningless tiny fragments.
3. Depth:
   - Aim for 1 to ${suggestedMaxDepth} levels of depth.
   - Only add a deeper level if it truly clarifies structure.
4. Content:
   - Use only information present in the original text.
   - You may rephrase slightly for clarity, but do not introduce new facts, examples, or claims.
5. Style:
   - Each 'text' entry should be a clear, concise bullet point (usually 1 sentence, or a short clause if the context is obvious).
   - No numbering, markdown, or bullet symbols. Just plain text.
6. Output format (IMPORTANT); every bullet object MUST have:
   - 'text': string
   - 'children': array (possibly empty)

Here is the text:

"${sourceText}"

"""

    def c = mapper.writeValueAsString(prompt)
    def requestBody = [
            model          : "gpt-4o-2024-08-06",
            input       : [
                    [
                            role   : "user",
                            content: c
                    ]
            ],
            text: [
                    format: [
                            name: "break",
                            type: "json_schema",
                            strict: true,
                            schema: jsonSchema
                    ]
            ]

    ]

    def request = HttpRequest.newBuilder()
            .uri(URI.create(openai))
            .header('Content-Type', 'application/json')
            .header('Authorization', "Bearer ${System.getenv("OPENAI")}")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
            .build()

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
    println "HTTP status: ${response.statusCode()}"

    if (response.statusCode() != 200) {
        UITools.errorMessage(
                "Decomposition HTTP error ${response.statusCode()}.\n" +
                        "Body: ${response.body()}"
        )
        return
    }

    // Parse Ollama chat response
    def jsonResponse = new JsonSlurper().parseText(response.body())
    def assistantContent = jsonResponse?.output.content.text[0]
    if (!assistantContent) {
        UITools.errorMessage("No 'message.content' field found in LLM response. ${jsonResponse}")
        return
    }

    println "\nRaw assistant content:\n${assistantContent}"

    // Now parse assistantContent as JSON according to the specified schema
    def outline
    try {
        outline = new JsonSlurper().parseText(assistantContent)
    } catch (Exception pe) {
        UITools.errorMessage(assistantContent)
        return
    }

    def bullets = outline?.bullets
    if (!(bullets instanceof List) || bullets.isEmpty()) {
        UITools.informationMessage("LLM returned no bullets to create.")
        return
    }

    // Optional: clear existing children before exploding
    current.children.toList().each { it.delete() }

    // Create children recursively
    createChildrenFromBullets(current, bullets)

    UITools.informationMessage("Node exploded into bullet hierarchy.")

} catch (Exception e) {
    UITools.errorMessage("Explode script error: ${e.class.simpleName}: ${e.message}")
}
