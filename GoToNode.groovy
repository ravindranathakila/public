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

// store (id, label) pairs
def allEntries = nodes.collect { n ->
    [id: n.id, label: makeLabel(n)]
}

// 2. Build the autosuggest dialog
Frame frame = UITools.getFrame()
JDialog dialog = new JDialog(frame, "Go to node (type to filter)", true)
dialog.setLayout(new BorderLayout())

JTextField field = new JTextField()
DefaultListModel<String> listModel = new DefaultListModel<>()
JList<String> list = new JList<>(listModel)
list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

// function to (re)populate list based on filter text
def refreshList = { String filterText ->
    listModel.removeAllElements()
    String ft = (filterText ?: "").toLowerCase()

    allEntries.each { e ->
        if (ft.isEmpty() || e.label.toLowerCase().contains(ft)) {
            listModel.addElement(e.label)
        }
    }

    if (listModel.size() > 0) {
        list.setSelectedIndex(0)
        list.ensureIndexIsVisible(0)
    }
}

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
