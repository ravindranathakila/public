// @ExecutionModes({ON_SINGLE_NODE})

import org.freeplane.core.ui.components.UITools
import javax.swing.*
import javax.swing.event.*
import java.awt.*
import java.awt.event.*

// 1. Collect all nodes from the current map
def map  = node.map
def root = map.root

def nodes = []
root.findAll().each { n ->
    nodes << n
}

// Label function: what you see in the list
def makeLabel = { n ->
    // "ID_123456789  |  Some node text"
    "${n.id}  |  ${n.text ?: ''}"
}

// store (id, label, node) pairs
def allEntries = nodes.collect { n ->
    [id: n.id, label: makeLabel(n), node: n]
}

// 2. Build the autosuggest dialog
Frame frame = UITools.getFrame()
JDialog dialog = new JDialog(frame, "Go to node (type to filter)", true)
dialog.setLayout(new BorderLayout())

// --- NEW: make dialog background translucent ---
dialog.setUndecorated(true)
try {
    dialog.setOpacity(0.85f)  // may be ignored on some platforms
} catch (Exception ignored) { }
dialog.setBackground(new Color(0, 0, 0, 80))
// ----------------------------------------------

JTextField field = new JTextField()
DefaultListModel<String> listModel = new DefaultListModel<>()
JList<String> list = new JList<>(listModel)
list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

// NEW: helper to move selection up/down
def moveSelection = { int delta ->
    int size = listModel.size()
    if (size == 0) return
    int idx = list.getSelectedIndex()
    if (idx < 0) idx = 0
    int newIdx = idx + delta
    if (newIdx < 0) newIdx = 0
    if (newIdx >= size) newIdx = size - 1
    if (newIdx != idx) {
        list.setSelectedIndex(newIdx)
        list.ensureIndexIsVisible(newIdx)
    }
}

// function to (re)populate list based on filter text
def refreshList = { String filterText ->
    listModel.removeAllElements()
    String ft = (filterText ?: "").toLowerCase()

    int added = 0                          // NEW: limit to 5 items
    allEntries.each { e ->
        if (ft.isEmpty() || e.label.split("\\|")[1].toLowerCase().contains(ft)) {
            if (added < 5) {               // NEW: enforce max 5
                listModel.addElement(e.label)
                added++
            }
        }
    }

    if (listModel.size() > 0) {
        list.setSelectedIndex(0)
        list.ensureIndexIsVisible(0)
        // selection listener will handle preview
    }
}

// --- NEW: whenever the selection changes, preview that node ---
list.addListSelectionListener({ e ->
    if (e.valueIsAdjusting) return

    String sel = list.getSelectedValue()
    if (sel == null) return

    def entry = allEntries.find { it.label == sel }
    if (entry?.node) {
        c.select(entry.node)
        c.centerOnNode(entry.node)
    }
} as ListSelectionListener)

// NEW: bind Cmd+Up / Cmd+Down on the text field (and list) to moveSelection
int shortcutMask = Toolkit.defaultToolkit.menuShortcutKeyMaskEx   // Cmd on macOS
KeyStroke ksUp   = KeyStroke.getKeyStroke(KeyEvent.VK_I,   shortcutMask)
KeyStroke ksDown = KeyStroke.getKeyStroke(KeyEvent.VK_K, shortcutMask)

// On the text field
InputMap fim = field.getInputMap(JComponent.WHEN_FOCUSED)
ActionMap fam = field.getActionMap()
fim.put(ksUp,   "cmd-up")
fim.put(ksDown, "cmd-down")
fam.put("cmd-up",   new AbstractAction() {
    void actionPerformed(ActionEvent e) { moveSelection(-1) }
})
fam.put("cmd-down", new AbstractAction() {
    void actionPerformed(ActionEvent e) { moveSelection(1) }
})

// Also on the list itself (in case focus is there)
InputMap lim = list.getInputMap(JComponent.WHEN_FOCUSED)
ActionMap lam = list.getActionMap()
lim.put(ksUp,   "cmd-up")
lim.put(ksDown, "cmd-down")
lam.put("cmd-up",   new AbstractAction() {
    void actionPerformed(ActionEvent e) { moveSelection(-1) }
})
lam.put("cmd-down", new AbstractAction() {
    void actionPerformed(ActionEvent e) { moveSelection(1) }
})

// initial full list
refreshList("")

// update list as the user types
field.document.addDocumentListener(new DocumentListener() {
    void insertUpdate(DocumentEvent e) { refreshList(field.text) }
    void removeUpdate(DocumentEvent e) { refreshList(field.text) }
    void changedUpdate(DocumentEvent e) { refreshList(field.text) }
})

// holder for result (Groovy map with one mutable slot)
def selectedIdHolder = [value: null]

// choose current selection and close dialog
def chooseCurrent = {
    String sel = list.getSelectedValue()
    if (sel == null) {
        if (listModel.size() == 0) {
            return
        }
        sel = listModel.getElementAt(0)
    }
    def entry = allEntries.find { it.label == sel }
    if (entry != null) {
        selectedIdHolder.value = entry.id
        dialog.dispose()
    }
}

// Enter in the text field = accept current list selection
field.addActionListener({ e ->
    chooseCurrent()
} as ActionListener)

// Double-click on a list row = accept that row
list.addMouseListener(new MouseAdapter() {
    @Override
    void mouseClicked(MouseEvent e) {
        if (e.clickCount == 2) {
            chooseCurrent()
        }
    }
})

// ESC closes the dialog
UITools.addEscapeActionToDialog(dialog, new AbstractAction() {
    void actionPerformed(ActionEvent e) {
        dialog.dispose()
    }
})

// Layout
dialog.add(field, BorderLayout.NORTH)
dialog.add(new JScrollPane(list), BorderLayout.CENTER)

dialog.setSize(500, 400)
UITools.setDialogLocationRelativeTo(dialog, node.delegate)
dialog.setVisible(true)  // blocks until dialog is closed

// 3. After dialog closes, act on selection
def id = selectedIdHolder.value
if (!id) {
    // user cancelled or closed without selection
    return
}

def target = map.node(id)
if (!target) {
    UITools.informationMessage("No node with id '${id}' found in this map.")
    return
}

// Select and center the node
c.select(target)
c.centerOnNode(target)
