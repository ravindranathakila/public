
// @ExecutionModes({ON_SINGLE_NODE})

@Grab('com.fasterxml.jackson.core:jackson-databind:2.18.0')
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

import groovy.json.JsonOutput
import org.freeplane.core.ui.components.UITools

/****************************************************
 * CONFIG SECTION
 ****************************************************/


// Always send full current branch? (root → ... → current)
boolean includeBranch = true

// Always send parent node?
boolean includeParent = true

// How many parents (ancestor levels) to walk up
// 1 = parent only, 2 = parent + grandparent, etc.
int parentLevels = 10

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
branch = branch.subList(1, branch.size())

// Parent text (if enabled)
def parentText = includeParent ? safeText(current.parent) : null

// Collect siblings: for each ancestor up to parentLevels,
// pick one sibling before and one after, plus all their children.
List<String> siblings = []

if (parentLevels > 0) {
    def ancestor = current
    for (int level = 1; level <= parentLevels; level++) {
        if (!ancestor) break
        def ancestorParent = ancestor.parent
        if (!ancestorParent) break  // root has no siblings

        def children = ancestorParent.children
        int idx = children.indexOf(ancestor)
        if (idx == -1) {
            // Should not happen but be defensive
            ancestor = ancestorParent.parent
            continue
        }

        // sibling before
        for (int x = idx - 1; x >= 0 ; x--) {
            def before = children[x]
            siblings.add(before.getBranchAsTextOutline())
        }

        // sibling after
        for (int x = idx + 1; x < children.size() ; x++) {
            def after = children[x]
            siblings.add(after.getBranchAsTextOutline())
        }

        // Move up one level
        ancestor = ancestorParent
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

//UITools.informationMessage(JsonOutput.prettyPrint(jsonBody))

/****************************************************
 * SEND TO LLM (Ollama chat API)
 ****************************************************/

try {

// Create HTTP client and JSON mapper
    def client = HttpClient.newHttpClient()
    def mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    def prompt = """
You are a disciplined mind attached to a concept graph (a mind map).

You are given a JSON object with these fields:

- "current": text of the currently selected node.
- "parent": text of the parent node (may be null or empty).
- "branch": array of node texts from the root down to the current node (may be empty).
- "siblings": array of node texts from related nodes at the same or higher levels (may be empty).

All of this text is the ONLY source of domain facts you are allowed to use.

Your job is to generate a SINGLE, well-formed text snippet that is suitable as the content of a new node attached under the current one.

GENERAL RULES

1. Treat all provided texts as canonical knowledge you may rely on.
   - You may rephrase, combine, and clarify them.
   - You should provide a concrete and final answer, if "current" is a question.

```json
|json|
```
"""

// Define request payload for Ollama


    // curl https://api.openai.com/v1/responses \
    //  -H "Authorization: Bearer $OPENAI_API_KEY" \
    //  -d '{
    //    "model": "gpt-5.1",
    //    "input": "Write a short bedtime story about a unicorn."
    //  }'

//    def requestBody = [
//            model   : 'llama3.2:3b',
//            stream  : false,
//            messages: [
//                    [
//                            role   : 'user',
//                            content: prompt.replace("|json|", jsonBody)
//                    ]
//            ]
//    ]


    def body = prompt.replace("|json|", jsonBody)

//    UITools.informationMessage(body)

    def requestBody = [
            model   : "gpt-5.1",
            input  : body
    ]

// Build and send request
    def request = HttpRequest.newBuilder()
//            .uri(URI.create("http://localhost:11434/api/chat"))
            .uri(URI.create("https://api.openai.com/v1/responses"))
//            .header('Content-Type', 'application/json')
            .header('Content-Type', 'application/json')
//            .header('Authorization', "")
            .header('Authorization', "Bearer ${System.getenv("OPENAI")}")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
            .build()

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
    println "HTTP status: ${response.statusCode()}"

    if (response.statusCode() != 200) {
        UITools.errorMessage(
                "LLM script HTTP error ${response.statusCode()}.\n" +
                        "Body: ${response.body()}"
        )
    }
    else {
        // Parse JSON response
        def json = mapper.readTree(response.body())
        println "\nRaw JSON:\n${mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json)}"


//        def assistantContent = json.path('message')?.path('content')?.asText(null)
        def assistantContent = json?.output[0].content[0].text.asText(null)

        String composed = (assistantContent ?: "").trim()

        if (!composed) {
            UITools.informationMessage("LLM script: Empty response from LLM.")
        } else {
            // Append as a single new child node under current
            def newNode = current.createChild(composed)
            newNode.folded = false
        }
    }
} catch (Exception e) {
    UITools.errorMessage("LLM script error: ${e.class.simpleName}: ${e.message}")
}