// @ExecutionModes({ON_SINGLE_NODE})

@Grab('com.fasterxml.jackson.core:jackson-databind:2.18.0')
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.freeplane.core.ui.components.UITools

/****************************************************
 * CONFIG SECTION
 ****************************************************/

// URL of your LLM gateway endpoint
// The endpoint should accept POST JSON and return JSON:
//   { "text": "<composed sentence or paragraph>" }
String gatewayUrl = "http://localhost:8000/compose"

// Always send full current branch? (root → ... → current)
boolean includeBranch = true

// Always send parent node?
boolean includeParent = true

// Sibling depth (0 = no siblings)
// 1 = siblings of current node
// 2 = + siblings of parent
// 3 = + siblings of grandparent, etc.
int siblingDepth = 1

/****************************************************
 * HELPER FUNCTIONS
 ****************************************************/

def safeText = { n -> n?.text ?: "" }

/****************************************************
 * COLLECT CONTEXT
 ****************************************************/

def current = node

// Branch: path from root to current (if enabled)
def branch = includeBranch ? current.pathToRoot*.text : []

// Parent text (if enabled)
def parentText = includeParent ? safeText(current.parent) : null

// Collect siblings up to N ancestor levels
List<String> siblings = []
if (siblingDepth > 0) {
    def levelNode = current
    for (int d = 1; d <= siblingDepth; d++) {
        def p = levelNode.parent
        if (p == null) break

        // Siblings at this level (exclude the node we came from)
        siblings.addAll(
                p.children
                        .findAll { it.id != levelNode.id }
                        *.text
        )

        levelNode = p
    }
}

// Build payload
def payload = [
        nodeId  : current.id,
        current : safeText(current),
        parent  : parentText,
        branch  : branch,
        siblings: siblings
]

def jsonBody = JsonOutput.toJson(payload)

/****************************************************
 * SEND TO LLM GATEWAY
 ****************************************************/

try {

// Create HTTP client and JSON mapper
    def client = HttpClient.newHttpClient()
    def mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    def prompt = """
You are a disciplined writing engine attached to a concept graph (a mind map).

You are given a JSON object with these fields:

- "current": text of the currently selected node.
- "parent": text of the parent node (may be null or empty).
- "branch": array of node texts from the root down to the current node (may be empty).
- "siblings": array of node texts from related nodes at the same or higher levels (may be empty).

All of this text is the ONLY source of domain facts you are allowed to use.

Your job is to generate a SINGLE, well-formed text snippet that is suitable as the content of a new node attached under the current one.

GENERAL RULES

1. Treat all provided texts as the canonical knowledge you may rely on.
   - You may rephrase, combine, and clarify them.
   - You may infer very generic relationships (e.g., “therefore”, “this means that”, “in summary”).
   - You MUST NOT invent concrete new facts, mechanisms, numbers, or named entities that are not clearly implied by the given text.

2. Your output must be self-contained:
   - Do not mention “branch”, “current node”, “parent”, “siblings”, “this graph”, or any UI concepts.
   - Do not refer to “the text above” or “the given context”.
   - Write as if this node will be read on its own within a conceptual hierarchy.

3. Style:
   - Use clear, precise sentences.
   - Prefer an analytic, slightly abstract tone, as if explaining ideas carefully.
   - Avoid rhetorical questions unless they are already present in the context.
   - No bullet lists, no headings, no markdown, no quotation marks around the whole answer.

4. Scope:
   - Aim for 1–3 sentences, unless the input is extremely short and only supports one.
   - If the current node looks like a heading, produce a sentence that elaborates or refines that heading using information from parent, branch, and siblings.
   - If the current node already looks like a full sentence, you may produce a more coherent or slightly more detailed restatement, again only using information from the provided texts.

5. Safety and discipline:
   - If the provided texts contain contradictions, produce a neutral synthesis or choose the more precise formulation; do not try to resolve contradictions with new invented information.
   - If you are unsure of a detail, leave it out rather than guessing.

Output ONLY the node text you propose. Do not include explanations, disclaimers, or any surrounding commentary.

```json
|json|
```
"""

// Define request payload
    def requestBody = [
            model   : 'smollm:135m',
            stream  : false,
            messages: [
                    [role: 'user', content: prompt.replaceFirst("|json|", jsonBody)]
            ]
    ]

// Build and send request
    def request = HttpRequest.newBuilder()
            .uri(URI.create('http://localhost:11434/api/chat'))
            .header('Content-Type', 'application/json')
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
            .build()

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
    println "HTTP status: ${response.statusCode()}"

    if (response.statusCode() != 200) {

        String errText = ""
        try {
            errText = conn.errorStream?.getText("UTF-8") ?: ""
        }
        catch (Exception ignored) {
        }

        UITools.errorMessage(
                "LLM script HTTP error ${status}.\n" +
                        (errText ? "Details: ${errText}" : "")
        )
    } else {


        // Parse JSON response
        def json = mapper.readTree(response.body())
        println "\nRaw JSON:\n${mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json)}"

        def assistantContent = json.path('message')?.path('content')?.asText(null)
        println "\nAssistant:\n${assistantContent ?: '[no content field found]'}"

        String composed = (assistantContent ?: "").trim()

        if (!composed) {
            UITools.informationMessage("LLM script: Empty response from gateway.")
        } else {
            // Append as a single new child node under current
            def newNode = current.createChild(composed)
            newNode.folded = false
            UITools.informationMessage("LLM script: New child node created.")
        }
    }

} catch (Exception e) {
    UITools.errorMessage("LLM script error: ${e.class.simpleName}: ${e.message}")
}

