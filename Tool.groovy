@Grab('dev.langchain4j:langchain4j-open-ai:1.9.1')
@Grab('dev.langchain4j:langchain4j-agentic:1.9.1-beta17')

import dev.langchain4j.agentic.Agent
import dev.langchain4j.agentic.AgenticServices
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import org.freeplane.api.NodeRO
import org.freeplane.core.ui.components.UITools

import java.nio.file.Files
import java.nio.file.Path

interface ContextGathererAgent {

    @SystemMessage("""
        You gather all information needed to solve the user's objective.
        Use the available tools to inspect files, query APIs, etc.
        Return a concise but complete context summary.
        """)
    @UserMessage("""
        Objective: {{objective}}
        Hints: {{hints}}
        """)
    @Agent(outputKey = "context")
    String gather(
            @V("objective") String objective,
            @V("hints")     String hints
    );
}


interface ExecutorAgent {

    @SystemMessage("""
        You execute steps from a plan using tools.
        For each step, decide which tools to call and in what order.
        Aggregate the results into a coherent draft answer.
        """)
    @UserMessage("""
        Objective: {{objective}}
        Plan: {{plan}}
        """)
    @Agent(outputKey = "draftAnswer")
    String execute(
            @V("objective") String objective,
            @V("plan")      String plan
    );
}

interface PlannerAgent {

    @SystemMessage("""
        You design an explicit, executable plan.
        Use the context to propose a sequence of steps that other agents can execute.
        Output a numbered list of concrete steps.
        """)
    @UserMessage("""
        Objective: {{objective}}
        Context: {{context}}
        """)
    @Agent(outputKey = "plan")
    String plan(
            @V("objective") String objective,
            @V("context")   String context
    );
}

interface SupervisorAgent {

    @SystemMessage("""
        You are a supervisor agent.
        You have access to specialized sub-agents, exposed as tools:
        - gatherContext(objective, hints)
        - makePlan(objective, context)
        - runPlan(objective, plan)

        Your job is to decide:
        - Which tool to call, in what order, and how many times.
        - When you have reached the end, stop and return an empty string is final answer.

        General strategy:
        1. If context is missing or insufficient, call gatherContext.
        2. Once you have enough context, call makePlan.
        3. Once you have a reasonable plan, call runPlan.
        4. If results are unsatisfactory, you may call these tools again
           (e.g. refine context, adjust plan, rerun plan).
        5. When you are satisfied, stop.
        """)
    @UserMessage("""
        Objective: {{objective}}
        Optional hints: {{hints}}
        """)
    @Agent(outputKey = "finalAnswer")
    String solve(
            @V("objective") String objective,
            @V("hints")     String hints
    );
}

NodeRO current = node

class AgentTools {

    private final ContextGathererAgent contextAgent;
    private final PlannerAgent        plannerAgent;
    private final ExecutorAgent       executorAgent;

    AgentTools(
            ContextGathererAgent contextAgent,
            PlannerAgent plannerAgent,
            ExecutorAgent executorAgent
    ) {
        this.contextAgent = contextAgent;
        this.plannerAgent = plannerAgent;
        this.executorAgent = executorAgent;
    }

    @dev.langchain4j.agent.tool.Tool("Gather all relevant context for an objective; returns a context string.")
    String gatherContext(String objective, String hints) {
        return contextAgent.gather(objective, hints);
    }

    @dev.langchain4j.agent.tool.Tool("Create a textual plan from an objective and its context.")
    String makePlan(String objective, String context) {
        return plannerAgent.plan(objective, context);
    }

    @dev.langchain4j.agent.tool.Tool("Execute a plan for an objective and return a draft answer.")
    String runPlan(String objective, String plan) {
        return executorAgent.execute(objective, plan);
    }
}



class FileTools {

    @dev.langchain4j.agent.tool.Tool("Read a UTF-8 text file from the local filesystem")
    static String readFile(String path) throws IOException {
        println "#readFile()/path/${path}"
        try {
            return Files.readString(Path.of(path));
        } catch (Throwable t) {
            println "#readFile()/!/${t.message}"
            throw t
        }
    }

    @dev.langchain4j.agent.tool.Tool("List all files under the given directory")
    static List<String> listFiles(String directory) {
        println "#listFiles()/directory/${directory}"
        try (var stream = Files.list(Path.of(directory))) {
            return stream
                    .map(p -> p.toAbsolutePath().toString())
                    .toList();
        } catch (Throwable t) {
            println "#readFile()/!/${t.message}"
            throw new RuntimeException(t);
        }
    }
}

class UserInteractionTools {

    NodeRO current

    UserInteractionTools(NodeRO current) {
        this.current = current
    }

    @dev.langchain4j.agent.tool.Tool("Notifies user with the message and returns true if successful, false if failed")
    static boolean notifyUser(String notificationMessage) {
        println "#notifyUser/notificationMessage/${notificationMessage}"
        try {
            UITools.informationMessage(notificationMessage)
            return true
        } catch (Throwable t) {
            println "#notifyUser()/!/${t.message}"
            return false
        }
    }

    @dev.langchain4j.agent.tool.Tool("Asks user a question")
    String askUser(String question) {
        println "#askUser/question/${question}"
        try {
            return UITools.showInputDialog(current.delegate, question, "")
        } catch (Throwable t) {
            println "#askUser()/!/${t.message}"
            return false
        }
    }

    @dev.langchain4j.agent.tool.Tool("Warn user of an error and returns true if successful, false if failed")
    static boolean warnUser(String error) {
        println "#warnUser/error/${error}"
        try {
            UITools.errorMessage(error)
            return  true
        } catch (Throwable t) {
            println "#warnUser()/!/${t.message}"
            return false
        }
    }
}

class LlmTools {

    NodeRO current

    LlmTools(NodeRO current) {
        this.current = current
    }

    @dev.langchain4j.agent.tool.Tool("Asks LLM a question and gets an answer")
    static boolean askLlm(String question) {
        println "#askLlm/question/${question}"
        try {
            ChatModel chatModel = OpenAiChatModel.builder()
                    .apiKey(System.getenv("OPENAI"))
                    .modelName("gpt-4.1-mini")
                    .build();
            return chatModel.chat(question)
        } catch (Throwable t) {
            println "#askLlm()/!/${t.message}"
            throw t
        }
    }
}

ChatModel chatModel = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI"))
        .modelName("gpt-4.1-mini")
        .build();

FileTools fileTools = new FileTools();
UserInteractionTools notificationTools = new UserInteractionTools(current);
LlmTools llmTools = new LlmTools(current);


List<Object> tools = [fileTools, notificationTools, llmTools]
ContextGathererAgent contextAgent = AgenticServices
        .agentBuilder(ContextGathererAgent.class)
        .chatModel(chatModel)
        .tools(tools.toArray())                 // <- leaf agent can call FileTools repeatedly
        .maxSequentialToolsInvocations(8) // safeguard
        .build();

PlannerAgent plannerAgent = AgenticServices
        .agentBuilder(PlannerAgent.class)
        .chatModel(chatModel)
        .build(); // pure reasoning, no tools

ExecutorAgent executorAgent = AgenticServices
        .agentBuilder(ExecutorAgent.class)
        .chatModel(chatModel)
        .tools(tools.toArray())
        .maxSequentialToolsInvocations(16)
        .build();
AgentTools agentTools = new AgentTools(contextAgent, plannerAgent, executorAgent);

SupervisorAgent supervisor = AgenticServices
        .agentBuilder(SupervisorAgent.class)
        .chatModel(chatModel)
        .tools(agentTools)                 // the “meta-tools” that call agents
        .maxSequentialToolsInvocations(12) // overall upper bound per top-level call
        .build();

String answer = supervisor.solve(
        node.displayedText,
        "Use tools in all situations. In case of error, use the warnUser tool to indicate it to the user and give up."
);

System.out.println("FINAL ANSWER:\n" + answer);

UITools.informationMessage(answer);
