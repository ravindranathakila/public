import org.freeplane.api.NodeRO
import org.freeplane.core.ui.components.UITools

// @ExecutionModes({ON_SINGLE_NODE})


NodeRO current = node


def prompt = UITools.showInputDialog(current.delegate, "Join String", "\n")

if (prompt == "" || prompt == null) {
    return
}

def promptLength = prompt.length()

StringBuilder sb = new StringBuilder()

current.children.findAll{   sb.append(it.displayedText).append(prompt)}

def length = sb.length()

current.createChild(sb.delete(length - promptLength, length))